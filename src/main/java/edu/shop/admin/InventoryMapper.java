package edu.shop.admin;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 库存流水是事实记录，页面不提供删除或直接修改流水的功能。 */
@Mapper
public interface InventoryMapper {
  /** 商品建档记录一次初始库存。 */
  @Insert(
      "INSERT INTO"
          + " inventory_log(product_id,change_qty,stock_after,type,reference_no,operator_id,remark)"
          + " VALUES(#{productId},#{quantity},#{quantity},'INITIAL','INIT',#{operatorId},#{remark})")
  int initial(
      @Param("productId") long productId,
      @Param("quantity") int quantity,
      @Param("operatorId") long operatorId,
      @Param("remark") String remark);

  /** 采购入库写正数变化。 */
  @Insert(
      "INSERT INTO"
          + " inventory_log(product_id,change_qty,stock_after,type,reference_no,operator_id,remark)"
          + " VALUES(#{productId},#{quantity},#{stockAfter},'PURCHASE',#{reference},#{operatorId},'采购入库')")
  int purchase(
      @Param("productId") long productId,
      @Param("quantity") int quantity,
      @Param("stockAfter") int stockAfter,
      @Param("reference") String reference,
      @Param("operatorId") long operatorId);

  /** 最新500条流水。 */
  @Select(
      "SELECT l.id,l.product_id AS productId,p.name AS productName,l.change_qty AS"
          + " changeQty,l.stock_after AS stockAfter,l.type,l.reference_no AS"
          + " referenceNo,l.remark,u.display_name AS operatorName,l.created_at AS createdAt FROM"
          + " inventory_log l JOIN product p ON l.product_id=p.id LEFT JOIN shop_user u ON"
          + " u.id=l.operator_id ORDER BY l.id DESC LIMIT 500")
  List<Map<String, Object>> list();
}
