package edu.shop.admin;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 后台管理SQL，所有写操作只由具有老板权限的服务调用。 */
@Mapper
public interface AdminMapper {
  /** 供应商列表。 */
  @Select("SELECT id,name,contact,phone FROM supplier ORDER BY id")
  List<Map<String, Object>> suppliers();

  /** 判断供应商存在。 */
  @Select("SELECT COUNT(*) FROM supplier WHERE id=#{id}")
  int supplierExists(long id);

  /** 添加供应商。 */
  @Insert("INSERT INTO supplier(name,contact,phone) VALUES(#{name},#{contact},#{phone})")
  int addSupplier(
      @Param("name") String name, @Param("contact") String contact, @Param("phone") String phone);

  /** 编辑供应商。 */
  @Update("UPDATE supplier SET name=#{name},contact=#{contact},phone=#{phone} WHERE id=#{id}")
  int updateSupplier(
      @Param("id") long id,
      @Param("name") String name,
      @Param("contact") String contact,
      @Param("phone") String phone);

  /** 删除供应商，有采购引用则外键拒绝。 */
  @Delete("DELETE FROM supplier WHERE id=#{id}")
  int deleteSupplier(long id);

  /** 插入采购主表，并获取生成主键。 */
  @Insert(
      "INSERT INTO purchase_order(supplier_id,operator_id,total_amount)"
          + " VALUES(#{supplierId},#{operatorId},#{totalAmount})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insertPurchase(PurchaseOrder order);

  /** 插入采购明细。 */
  @Insert(
      "INSERT INTO purchase_item(purchase_id,product_id,quantity,unit_cost,subtotal)"
          + " VALUES(#{purchaseId},#{productId},#{quantity},#{unitCost},#{subtotal})")
  int purchaseItem(
      @Param("purchaseId") long purchaseId,
      @Param("productId") long productId,
      @Param("quantity") int quantity,
      @Param("unitCost") BigDecimal unitCost,
      @Param("subtotal") BigDecimal subtotal);

  /** 最近200笔采购。 */
  @Select(
      "SELECT p.id,p.supplier_id AS supplierId,s.name AS supplierName,u.display_name AS"
          + " operatorName,p.total_amount AS totalAmount,p.created_at AS createdAt FROM"
          + " purchase_order p JOIN supplier s ON p.supplier_id=s.id JOIN shop_user u ON"
          + " p.operator_id=u.id ORDER BY p.id DESC LIMIT 200")
  List<Map<String, Object>> purchases();

  /** 某笔采购的明细。 */
  @Select(
      "SELECT i.product_id AS productId,p.name AS productName,i.quantity,i.unit_cost AS"
          + " unitCost,i.subtotal FROM purchase_item i JOIN product p ON i.product_id=p.id WHERE"
          + " i.purchase_id=#{id} ORDER BY i.product_id")
  List<Map<String, Object>> purchaseItems(long id);

  /** 读取全部数码店设置。 */
  @Select("SELECT setting_key AS settingKey,setting_value AS settingValue FROM shop_setting")
  List<Map<String, Object>> settings();

  /** 改一个已有设置。 */
  @Update("UPDATE shop_setting SET setting_value=#{value} WHERE setting_key=#{key}")
  int updateSetting(@Param("key") String key, @Param("value") String value);

  /** 成交与退款后的实时汇总，只计PAID。 */
  @Select(
      "SELECT COALESCE(SUM(CASE WHEN CAST(paid_at AS DATE)=CURRENT_DATE THEN payable_amount ELSE 0"
          + " END),0) AS todaySales,COALESCE(SUM(payable_amount),0) AS totalSales,COUNT(*) AS"
          + " paidOrderCount FROM shop_order WHERE status='PAID' AND request_key NOT LIKE 'demo:%'")
  Map<String, Object> totals();

  /** 会员数。 */
  @Select(
      "SELECT COUNT(*) FROM shop_user WHERE role='MEMBER' AND LOWER(SUBSTRING(username,1,5)) !="
          + " 'demo_'")
  int members();

  /** 正常商品数。 */
  @Select("SELECT COUNT(*) FROM product WHERE sku NOT LIKE 'DEMO-%'")
  int products();

  /** 按日期销售额。 */
  @Select(
      "SELECT CAST(paid_at AS DATE) AS date,SUM(payable_amount) AS amount FROM shop_order WHERE"
          + " status='PAID' AND request_key NOT LIKE 'demo:%' GROUP BY CAST(paid_at AS DATE) ORDER"
          + " BY date DESC LIMIT 14")
  List<Map<String, Object>> dailySales();

  /** 热销商品以成交快照统计，退款不计。 */
  @Select(
      "SELECT i.product_name AS name,SUM(i.quantity) AS quantity,SUM(i.subtotal) AS amount FROM"
          + " order_item i JOIN shop_order o ON i.order_id=o.id WHERE o.status='PAID' AND"
          + " o.request_key NOT LIKE 'demo:%' GROUP BY i.product_id,i.product_name ORDER BY"
          + " quantity DESC LIMIT 8")
  List<Map<String, Object>> hotProducts();

  /** 销售员个人业绩。 */
  @Select(
      "SELECT u.display_name AS name,COUNT(o.id) AS orderCount,COALESCE(SUM(o.payable_amount),0) AS"
          + " amount FROM shop_user u LEFT JOIN shop_order o ON o.cashier_id=u.id AND"
          + " o.status='PAID' AND o.request_key NOT LIKE 'demo:%' WHERE u.role='SALES' GROUP BY"
          + " u.id,u.display_name ORDER BY amount DESC")
  List<Map<String, Object>> cashierPerformance();

  /** 导出日常订单白名单字段，排除并发实验；无密码或会话字段。 */
  @Select(
      "SELECT o.order_no AS orderNo,o.status,COALESCE(m.display_name,'散客') AS"
          + " memberName,c.display_name AS cashierName,o.original_amount AS"
          + " originalAmount,o.payable_amount AS payableAmount,o.created_at AS createdAt FROM"
          + " shop_order o LEFT JOIN shop_user m ON o.member_id=m.id LEFT JOIN shop_user c ON"
          + " c.id=o.cashier_id WHERE o.request_key NOT LIKE 'demo:%' ORDER BY o.id DESC")
  List<Map<String, Object>> exportOrders();
}
