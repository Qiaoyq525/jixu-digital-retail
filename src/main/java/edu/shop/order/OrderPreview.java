package edu.shop.order;

import java.math.BigDecimal;
import java.util.List;

/** 只读结账试算结果。试算不占库存，最终支付会在事务中重新计算与验证。 */
public record OrderPreview(
    BigDecimal originalAmount,
    BigDecimal productDiscount,
    BigDecimal memberDiscount,
    BigDecimal fullReduction,
    BigDecimal payableAmount,
    int earnedPoints,
    String discountDescription,
    List<Line> items) {
  public record Line(
      long productId,
      String name,
      BigDecimal unitPrice,
      int quantity,
      BigDecimal subtotal,
      String sku,
      String description,
      String imageUrl) {
    /** 兼容仅关心金额的调用方。 */
    public Line(long productId, String name, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {
      this(productId, name, unitPrice, quantity, subtotal, "", "", "");
    }
  }
}
