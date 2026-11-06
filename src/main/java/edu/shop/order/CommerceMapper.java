package edu.shop.order;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** MyBatis 数据访问接口。#{...} 是预编译参数占位符，避免拼接用户输入造成 SQL 注入。 */
@Mapper
public interface CommerceMapper {
  /** 查会员公开业务字段；lock=true 的版本由另一个方法实现行锁。 */
  @Select(
      "SELECT id,username,display_name,role,points,total_spent,enabled FROM shop_user WHERE"
          + " id=#{id}")
  MemberRow member(long id);

  /** 锁定会员行，使支付、退货、购物车与排队的同会员操作顺序执行。 */
  @Select(
      "SELECT id,username,display_name,role,points,total_spent,enabled FROM shop_user WHERE"
          + " id=#{id} FOR UPDATE")
  MemberRow lockMember(long id);

  /** 读取商品的结账所需字段。 */
  @Select("SELECT id,sku,name,description,image_url,price,stock,on_sale,emoji FROM product WHERE id=#{id}")
  ProductRow product(long id);

  /** 数据库行锁：多个结账线程不能同时修改同一商品库存。 */
  @Select("SELECT id,sku,name,description,image_url,price,stock,on_sale,emoji FROM product WHERE id=#{id} FOR UPDATE")
  ProductRow lockProduct(long id);

  /** 读取老板配置的单个优惠参数。 */
  @Select("SELECT setting_value FROM shop_setting WHERE setting_key=#{key}")
  String setting(String key);

  /** 有条件地扣库存；返回 0 表示库存不足或商品已下架。 */
  @Update(
      "UPDATE product SET stock=stock-#{quantity} WHERE id=#{id} AND on_sale=TRUE AND"
          + " stock>=#{quantity}")
  int decreaseStock(@Param("id") long id, @Param("quantity") int quantity);

  /** 退货还原库存，库存增量来自原订单明细。 */
  @Update("UPDATE product SET stock=stock+#{quantity} WHERE id=#{id}")
  int restoreStock(@Param("id") long id, @Param("quantity") int quantity);

  /** 写入订单主表，数据库生成的主键回填到 order.id。 */
  @Insert(
      """
      INSERT INTO shop_order(order_no,member_id,cashier_id,status,original_amount,product_discount,
        member_discount,full_reduction,payable_amount,earned_points,discount_description,request_key,paid_at)
      VALUES(#{orderNo},#{memberId},#{cashierId},#{status},#{originalAmount},#{productDiscount},
        #{memberDiscount},#{fullReduction},#{payableAmount},#{earnedPoints},#{discountDescription},#{requestKey},CURRENT_TIMESTAMP)
      """)
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insertOrder(SaleOrder order);

  /** 保存购买时的名称、货号、规格、图路径和单价；未来编辑商品不改变历史订单。 */
  @Insert(
      """
      INSERT INTO order_item(order_id,product_id,product_name,sku,description,image_url,unit_price,quantity,subtotal)
      VALUES(#{orderId},#{productId},#{productName},#{sku},#{description},#{imageUrl},#{unitPrice},#{quantity},#{subtotal})
      """)
  int insertItem(OrderItem item);

  /** 记录库存变动，流水和库存扣减属于同一个事务。 */
  @Insert(
      """
      INSERT INTO inventory_log(product_id,change_qty,stock_after,type,reference_no,operator_id,remark)
      VALUES(#{productId},#{change},#{stockAfter},#{type},#{reference},#{operatorId},#{remark})
      """)
  int inventory(
      @Param("productId") long productId,
      @Param("change") int change,
      @Param("stockAfter") int stockAfter,
      @Param("type") String type,
      @Param("reference") String reference,
      @Param("operatorId") Long operatorId,
      @Param("remark") String remark);

  /** 同时更新积分与净累计消费；金额和积分增量也可以为负数，用于退款。 */
  @Update(
      "UPDATE shop_user SET points=points+#{points},total_spent=total_spent+#{amount} WHERE"
          + " id=#{id}")
  int updateMember(
      @Param("id") long id, @Param("points") int points, @Param("amount") BigDecimal amount);

  /** 保存积分变动前因和变动后的余额。 */
  @Insert(
      """
      INSERT INTO point_log(member_id,change_points,balance_after,order_id,type)
      VALUES(#{memberId},#{change},#{balance},#{orderId},#{type})
      """)
  int points(
      @Param("memberId") long memberId,
      @Param("change") int change,
      @Param("balance") int balance,
      @Param("orderId") long orderId,
      @Param("type") String type);

  /** 业务操作日志，禁止将原始密码等秘密写入 detail。 */
  @Insert(
      "INSERT INTO audit_log(operator_id,action,detail) VALUES(#{operatorId},#{action},#{detail})")
  int audit(
      @Param("operatorId") Long operatorId,
      @Param("action") String action,
      @Param("detail") String detail);

  /** 幂等键唯一：同一个支付请求只能产生一张订单。 */
  @Select("SELECT * FROM shop_order WHERE request_key=#{key}")
  SaleOrder byRequestKey(String key);

  /** 读取订单，用于判断所有权及确定退款需要锁定的会员。 */
  @Select("SELECT * FROM shop_order WHERE id=#{id}")
  SaleOrder order(long id);

  /** 锁定原订单，避免重复退款。 */
  @Select("SELECT * FROM shop_order WHERE id=#{id} FOR UPDATE")
  SaleOrder lockOrder(long id);

  /** 查询订单及显示名称，身份过滤条件由服务端角色决定。 */
  @Select(
      """
      <script>SELECT o.*,m.display_name member_name,c.display_name cashier_name
      FROM shop_order o LEFT JOIN shop_user m ON m.id=o.member_id LEFT JOIN shop_user c ON c.id=o.cashier_id
      <where>o.request_key NOT LIKE 'demo:%' <if test="memberId != null">AND o.member_id=#{memberId}</if>
      <if test="cashierId != null">AND o.cashier_id=#{cashierId}</if></where>
      ORDER BY o.id DESC LIMIT 500</script>
      """)
  List<SaleOrder> orders(@Param("memberId") Long memberId, @Param("cashierId") Long cashierId);

  /** 老板审核待办不受最近 500 笔普通订单限制，先显示全部待处理申请。 */
  @Select("""
      SELECT o.*,m.display_name member_name,c.display_name cashier_name
      FROM shop_order o LEFT JOIN shop_user m ON m.id=o.member_id
      LEFT JOIN shop_user c ON c.id=o.cashier_id
      WHERE o.refund_status<>'NONE' AND o.request_key NOT LIKE 'demo:%'
      ORDER BY CASE WHEN o.refund_status='PENDING' THEN 0 ELSE 1 END,o.refund_requested_at DESC,o.id DESC
      """)
  List<SaleOrder> refundOrders();

  /** 读取按商品编号排序的原订单明细，退款按相同顺序加库存锁。 */
  @Select("SELECT * FROM order_item WHERE order_id=#{orderId} ORDER BY product_id")
  List<OrderItem> items(long orderId);

  /** 会员的退款申请只改变审批状态，支付、库存、积分均保持原样。 */
  @Update("""
      UPDATE shop_order SET refund_status='PENDING',refund_reason=#{reason},refund_review_note='',
        refund_requested_at=CURRENT_TIMESTAMP,refund_reviewed_at=NULL
      WHERE id=#{id} AND status='PAID' AND refund_status IN ('NONE','REJECTED')
      """)
  int requestRefund(@Param("id") long id, @Param("reason") String reason);

  /** 审核状态与实际退款在同一个事务中提交，条件更新阻止重复审核。 */
  @Update("""
      UPDATE shop_order SET refund_status=#{status},refund_review_note=#{note},refund_reviewed_at=CURRENT_TIMESTAMP
      WHERE id=#{id} AND refund_status='PENDING'
      """)
  int reviewRefund(@Param("id") long id, @Param("status") String status, @Param("note") String note);

  /** 条件更新保证只有已支付订单可以退货。 */
  @Update(
      "UPDATE shop_order SET status='REFUNDED',refunded_at=CURRENT_TIMESTAMP WHERE id=#{id} AND"
          + " status='PAID'")
  int markRefunded(long id);

  /** 读取当前会员购物车，商品实时信息由关联查询取得。 */
  @Select(
      """
      SELECT c.id,c.product_id,p.sku,p.name,p.description,p.image_url,p.price,p.stock,p.on_sale,p.emoji,c.quantity
      FROM cart_item c JOIN product p ON p.id=c.product_id WHERE c.member_id=#{memberId} ORDER BY c.id
      """)
  List<CartItem> cart(long memberId);

  /** 查购物车某一行，会员锁保证新增与修改时不会出现同会员并发冲突。 */
  @Select("SELECT id FROM cart_item WHERE member_id=#{memberId} AND product_id=#{productId}")
  Long cartId(@Param("memberId") long memberId, @Param("productId") long productId);

  /** 加入购物车。 */
  @Insert(
      "INSERT INTO cart_item(member_id,product_id,quantity)"
          + " VALUES(#{memberId},#{productId},#{quantity})")
  int addCart(
      @Param("memberId") long memberId,
      @Param("productId") long productId,
      @Param("quantity") int quantity);

  /** 把购物车数量设置为页面提交的绝对数量。 */
  @Update(
      "UPDATE cart_item SET quantity=#{quantity} WHERE member_id=#{memberId} AND"
          + " product_id=#{productId}")
  int updateCart(
      @Param("memberId") long memberId,
      @Param("productId") long productId,
      @Param("quantity") int quantity);

  /** 删除自己购物车的一行。 */
  @Delete("DELETE FROM cart_item WHERE member_id=#{memberId} AND product_id=#{productId}")
  int removeCart(@Param("memberId") long memberId, @Param("productId") long productId);

  /** 快照数量已覆盖当前购物车行时直接删除，避免把数量更新为0而违反正数约束。 */
  @Delete(
      "DELETE FROM cart_item WHERE member_id=#{memberId} AND product_id=#{productId} AND"
          + " quantity<=#{quantity}")
  int removeConsumedCart(
      @Param("memberId") long memberId,
      @Param("productId") long productId,
      @Param("quantity") int quantity);

  /** 付款后仅减去入队快照中的数量，保留排队期间新加入的同商品数量。 */
  @Update(
      "UPDATE cart_item SET quantity=quantity-#{quantity} WHERE member_id=#{memberId} AND"
          + " product_id=#{productId} AND quantity>#{quantity}")
  int consumeCart(
      @Param("memberId") long memberId,
      @Param("productId") long productId,
      @Param("quantity") int quantity);

  /** 删除结账后数量归零的购物车行。 */
  @Delete("DELETE FROM cart_item WHERE member_id=#{memberId} AND quantity=0")
  int clearEmptyCart(long memberId);

  /** 会员只允许读取自己的积分流水。 */
  @Select(
      "SELECT id,change_points,balance_after,order_id,type,created_at FROM point_log WHERE"
          + " member_id=#{memberId} ORDER BY id DESC LIMIT 300")
  List<PointEntry> pointEntries(long memberId);

  /** 柜台只能分配仍然启用的销售员，不会把老板冒充成销售员。 */
  @Select("SELECT id FROM shop_user WHERE role='SALES' AND enabled=TRUE ORDER BY id")
  List<Long> enabledCashiers();
}
