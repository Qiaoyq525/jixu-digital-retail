# 极序数码 · 版本3实施约定

根目录是本文件所在目录。Java 21 + Spring Boot 3.5.16 + MyBatis starter 3.0.4 + MySQL 8.4；同源静态 HTML/CSS/JS，无 Node。包名 edu.shop。金额 BigDecimal、数量正整数、SQL 全部参数化。普通 Java POJO 写 getter/setter 和中文方法注释，不用 Lombok；请求响应可以用 record 并解释。

## 目录所有权
- 主代理：pom、application.yml、数据库迁移、common/config/security/user/catalog/admin、scripts、集成测试、启动。
- commerce 代理：edu.shop.order、edu.shop.discount、edu.shop.queue，以及对应单元测试；可读取其他模块，不修改其文件。
- frontend 代理：src/main/resources/static 下全部文件；独立完成视觉与页面。
- docs 代理：docs 下文档与 README.md；后续只读评审。

## 数码零售背景
店名极序数码（JIXU DIGITAL），老板乔胤淇，界面称呼乔先生，登录用户名admin不变。四类电子商品：手机通讯、电脑整机、智能穿戴、显卡硬件。型号参数放商品description，当前不做序列号或保修工单。V2迁移条件替换旧演示资料，保留历史快照、库存、购物车和订单关系。

## 基础约定
响应正常为 ApiResponse<T>(boolean success, T data, String message)，JSON {success:true,data:...,message:"ok"}；失败 {success:false,data:null,message:"中文原因"} 并正确 HTTP 状态码。
common.BusinessException(String message) 默认400；common.BusinessException(int status,String message)。
security.CurrentUser 为 record(long id,String username,String displayName,Role role)，Role枚举 ADMIN,SALES,MEMBER 位于 user 包。security.AuthContext.require() 返回当前用户，requireRole(Role...) 验角色。通过 session 提取，不接收客户端用户身份。会员提交的收银员和柜台由服务端分配，确认时以登录 SALES 身份校验票据归属。
使用 Spring Security session、BCrypt、CSRF；GET /api/auth/csrf 返回 data:{token,headerName}，所有写操作带此header。POST /api/auth/login {username,password}，POST /api/auth/register {username,password,displayName}；GET /api/auth/me 返回 data:{id,username,displayName,role,points,totalSpent,level,discountRate}；POST /api/auth/logout。

## 数据库（主代理建表；其他代理按此写SQL）
所有主键 BIGINT AUTO_INCREMENT，created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP。金额 DECIMAL(12,2)，率 DECIMAL(5,2)。
shop_user(id,username UNIQUE,password_hash,display_name,role,points INT,total_spent DECIMAL,enabled BOOLEAN,created_at)。角色字段 VARCHAR。
category(id,name UNIQUE,created_at)。
product(id,category_id,sku UNIQUE,name,description,price,stock INT,low_stock_threshold INT,on_sale BOOLEAN,emoji VARCHAR(16),created_at)。
supplier(id,name,contact,phone,created_at)。
purchase_order(id,supplier_id,operator_id,total_amount,created_at)。
purchase_item(id,purchase_id,product_id,quantity,unit_cost,subtotal)。
inventory_log(id,product_id,change_qty,stock_after,type VARCHAR(30),reference_no VARCHAR(64),operator_id,remark,created_at)。
cart_item(id,member_id,product_id,quantity,UNIQUE(member_id,product_id))。
shop_order(id,order_no UNIQUE,member_id NULL,cashier_id NULL,status VARCHAR(24),original_amount,product_discount,member_discount,full_reduction,payable_amount,earned_points INT,discount_description VARCHAR(500),request_key VARCHAR(100) UNIQUE,created_at,paid_at TIMESTAMP NULL,refunded_at TIMESTAMP NULL)。状态 PAID,REFUNDED。V5追加refund_status(NONE/PENDING/APPROVED/REJECTED)、refund_reason、refund_review_note、refund_requested_at、refund_reviewed_at，原支付状态保持独立。
order_item(id,order_id,product_id,product_name,unit_price,quantity,subtotal)。
point_log(id,member_id,change_points,balance_after,order_id,type,created_at)。
audit_log(id,operator_id NULL,action,detail,created_at)。
shop_setting(setting_key VARCHAR(50) PRIMARY KEY,setting_value VARCHAR(500))，键 PRODUCT_DISCOUNT_RATE 默认1.00、FULL_REDUCTION_THRESHOLD默认3000、FULL_REDUCTION_AMOUNT默认100、COUNTER_COUNT默认2。
checkout_ticket(id,member_id,cashier_id NULL,status VARCHAR(24),payload_json TEXT,request_key UNIQUE,order_id NULL,error_message VARCHAR(500),counter_no INT NULL,created_at,updated_at)。状态 WAITING,PROCESSING,COMPLETED,FAILED,CANCELLED。新票据入库时即保存会员、指定 SALES、固定柜台与商品数量快照。重启保留已完整分配的 WAITING；优先恢复已有订单的票据，遗留 PROCESSING 或没有柜台归属的旧等待票据按恢复规则结束。不得调用自己内部@Transactional方法假装生效。
checkout_counter(counter_no 主键,cashier_id 唯一且可空)。V4追加4个固定柜台，默认绑定现有销售员；账号停用不改变既有柜台身份。COUNTER_COUNT仅限制接收新提交的柜台范围，已分配等待单仍可由原启用员工办理。

## 商品与管理API（主代理）
GET /api/categories =>[{id,name}]；POST {name} / PUT /api/categories/{id} {name} / DELETE...
GET /api/products?keyword=&categoryId= => [{id,categoryId,categoryName,sku,name,description,price,stock,lowStockThreshold,onSale,emoji}]。会员只能看上架商品，员工全部。
POST /api/products 同字段；PUT /api/products/{id} 修改商品信息(不允许借此直接改库存)；DELETE 为有引用时拒绝、推荐下架。PATCH /api/products/{id}/sale {onSale}。
GET /api/suppliers；POST {name,contact,phone}；PUT /{id}；DELETE /{id}。
GET /api/purchases 返回采购及 items；POST {supplierId,items:[{productId,quantity,unitCost}]} => 采购。
GET /api/inventory => 流水含 productName,operatorName。
GET /api/users => 用户公开字段(无密码哈希)；PATCH /api/users/{id}/enabled {enabled}，仅 ADMIN，禁止禁用自己。
GET /api/cashiers 仅ADMIN，返回cashiers/counters/limit/total；POST /api/cashiers {username,password,displayName,counterNo} 创建SALES，最多4个账号含停用，密码BCrypt；PUT /api/cashiers/{id}/counter {counterNo} 调至空闲柜台；PATCH /api/cashiers/{id}/enabled {enabled} 启停。创建/调岗/启停先锁固定柜台再检查约束，有WAITING/PROCESSING拒绝调岗或停用；旧用户启停入口对SALES使用同一规则。新增或调岗时必要地提高COUNTER_COUNT，保证安排的柜台可接单。
GET /api/settings => {productDiscountRate,fullReductionThreshold,fullReductionAmount,counterCount}；PUT 相同JSON仅ADMIN。
GET /api/stats => {todaySales,totalSales,paidOrderCount,memberCount,productCount,lowStock:[商品],dailySales:[{date,amount}],hotProducts:[{name,quantity,amount}],cashierPerformance:[{name,orderCount,amount}]}，仅ADMIN。
GET /api/audit => [{id,operatorName,action,detail,createdAt}]仅ADMIN。
GET /api/export/orders.csv 仅ADMIN，CSV带BOM、正确转义并处理公式注入。

## 商务API（commerce代理）
GET /api/cart => [{id,productId,name,price,stock,onSale,emoji,quantity,subtotal}]；PUT /api/cart {productId,quantity}（0删除）、DELETE /api/cart/{productId}。
POST /api/orders/preview {memberId:可空,items:[{productId,quantity}]} => {originalAmount,productDiscount,memberDiscount,fullReduction,payableAmount,earnedPoints,discountDescription,items:[{productId,name,unitPrice,quantity,subtotal}]}。
POST /api/orders/checkout {memberId:可空,items:[...],requestKey} => 订单。仅员工；会员必须排队。memberId 用于员工收银选会员，不得伪造销售员。member自身preview取自己。
GET /api/orders => [{id,orderNo,memberId,memberName,cashierId,cashierName,status,originalAmount,productDiscount,memberDiscount,fullReduction,payableAmount,earnedPoints,discountDescription,createdAt,paidAt,refundedAt,items:[{productId,productName,unitPrice,quantity,subtotal}]}]，老板全部、销售仅自己、会员仅自己。
GET /api/orders/{id} 同权限，返回当前退款申请与审核字段。POST /api/orders/{id}/refund-request {reason} 仅订单所属MEMBER申请。POST /api/orders/{id}/refund-review {approve,reason} 仅ADMIN处理PENDING申请；拒绝reason必填，会员可重新申请。申请/拒绝不改变库存积分；批准才整单退库存、扣回原积分、累计消费回退，全事务防重复。SALES仅查看本人经手订单；旧POST /api/orders/{id}/refund禁止执行，老板也不能绕过会员申请。
GET /api/points => 自己的积分流水；仅会员。
POST /api/queue/join {requestKey} 会员购物车快照入队，不接收其他用户身份；按可用柜台等待数最少、编号优先分配，返回已分配的 WAITING 票据，不自动支付。
GET /api/queue => {counters:[{counterNo,cashierId,cashierName,cashierUsername,status,waitingCount}],tickets:[{id,memberId,memberName,cashierId,cashierName,cashierUsername,status,orderId,errorMessage,counterNo,createdAt,position,items,totalAmount,previewError}],waitingCount,processingCount}。items含productId,name,sku,imageUrl,quantity,unitPrice,lineTotal。会员仅本人，SALES仅分配给本人，ADMIN可看全部；柜台展示不泄露其他顾客标识。金额由服务端当前价格及优惠试算，确认时再次验证。
POST /api/queue/{id}/confirm 仅票据指定且仍启用的SALES；ADMIN、其他销售员、会员均不能代确认。同柜台按提交顺序，只允许确认position=1的等待票据。成功返回完成票据及orderId；重复确认返回同一结果。柜台、会员、票据的锁按固定顺序取得，认领、支付、完成同事务；失败回滚并保留WAITING，页面提示原因。
POST /api/queue/{id}/cancel 会员取消自己的WAITING，取消与确认只允许一个成功。限量抢购实验接口已移除。

## 业务规则
等级由净累计消费 total_spent 算：<5000 普通1.00，>=5000银卡.98，>=15000金卡.95，>=30000黑金.90。优惠按普通全场折扣→会员折扣→折后满3000减100，逐步四舍五入到分，最低0；积分=floor(实付)，退回原earned_points。先锁会员再按product id升序SELECT FOR UPDATE，检查上架与库存，库存条件扣减、订单/明细/流水/积分同事务；幂等request_key归属校验。退款同锁顺序并避免与支付死锁。业务排队由数据库持久化且必须人工确认，不能为展示线程池而自动支付。固定柜台与销售员绑定，停用或未绑定柜台不接新单。真实订单并发竞争由隔离自动化测试验证，不在门店页面提供人工抢购实验。

## 前端风格
极序数码 / JIXU DIGITAL，中文，石墨与蓝青色科技风、清晰侧栏、丰富但克制；登录页电子设备展示与角色工作台。商品电子设备SVG与emoji视觉、搜索分类、购物车/收银台、订单详情、会员申请退款与老板审核、管理CRUD、采购、会员、统计图表(原生SVG/Canvas)、排队管理与收银员管理、优惠设置/日志。所有可见按钮有实现，错误toast，busy避免重复提交、金额由后端确认。前端不得innerHTML直接插用户内容，统一escapeHtml；API请求credentials same-origin并带CSRF。不能外部CDN依赖。登录预填演示账号，不存密码localStorage。

演示账号admin/Admin@12345、cashier1/Sales@12345、cashier2/Sales@12345、member1/Member@12345、member2/Member@12345。仅首次空库种子；密码BCrypt并无API暴露哈希。测试隔离用H2 MySQL mode做自动测试，真实MySQL再集成验证。

## 2026-11-01 本轮更新
当前目录所有权以本轮任务分工为准。商品扩展为五类14款，含键鼠外设。V3增加商品image_url和订单sku/description/image_url快照，商品、购物车、试算、订单响应均提供imageUrl。会员前端member-ui.js、员工增强staff-ui.js通过app.js的api()接入Java。最终IDEA目录位于C:\Users\Q\Desktop\java\版本3。
本轮追加V4固定柜台；会员提交立即展示柜台编号、员工姓名和账号，SALES收银工作台只确认自己的会员等待单。旧的自动消费业务队列已被此规则替代。2026-11-02按用户反馈移除隔离抢购实验，增加管理员收银员管理。

