package edu.shop.order;

/** 数据库查询映射对象：通过 getter/setter 读写，不包含密码字段。 */
public class OrderItem {
  // V2 改版复用了这些旧种子商品的编号；没有 SKU 快照的旧演示明细只能保留查看。
  private static final java.util.Set<String> LEGACY_DEMO_PRODUCT_NAMES =
      java.util.Set.of(
          "牧场鲜牛奶", "冷萃拿铁", "白桃乌龙茶", "气泡柠檬水", "黄油可颂", "黑巧曲奇",
          "每日坚果", "帆布小提袋", "陶瓷随手杯", "方格手账本", "极简中性笔", "彩色便利贴");

  private Long id;
  private Long orderId;
  private Long productId;
  private String productName;
  private java.math.BigDecimal unitPrice;
  private Integer quantity;
  private java.math.BigDecimal subtotal;

  private String sku;
  private String description;
  private String imageUrl;
  /** 获取 id 字段。 */
  public Long getId() {
    return id;
  }

  /** 设置 id 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setId(Long id) {
    this.id = id;
  }

  /** 获取 orderId 字段。 */
  public Long getOrderId() {
    return orderId;
  }

  /** 设置 orderId 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setOrderId(Long orderId) {
    this.orderId = orderId;
  }

  /** 获取 productId 字段。 */
  public Long getProductId() {
    return productId;
  }

  /** 设置 productId 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setProductId(Long productId) {
    this.productId = productId;
  }

  /** 获取 productName 字段。 */
  public String getProductName() {
    return productName;
  }

  /** 设置 productName 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setProductName(String productName) {
    this.productName = productName;
  }

  /** 获取 unitPrice 字段。 */
  public java.math.BigDecimal getUnitPrice() {
    return unitPrice;
  }

  /** 设置 unitPrice 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setUnitPrice(java.math.BigDecimal unitPrice) {
    this.unitPrice = unitPrice;
  }

  /** 获取 quantity 字段。 */
  public Integer getQuantity() {
    return quantity;
  }

  /** 设置 quantity 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setQuantity(Integer quantity) {
    this.quantity = quantity;
  }

  /** 获取 subtotal 字段。 */
  public java.math.BigDecimal getSubtotal() {
    return subtotal;
  }

  /** 设置 subtotal 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setSubtotal(java.math.BigDecimal subtotal) {
    this.subtotal = subtotal;
  }
  /** 读取购买时的货号快照；老订单没有记录时为空。 */
  public String getSku() {
    return sku;
  }

  /** 写入付款时复制的货号。 */
  public void setSku(String value) {
    sku = value;
  }

  /** 读取购买时规格，后续修改商品不会改变这份内容。 */
  public String getDescription() {
    return description;
  }

  /** 写入付款时复制的规格。 */
  public void setDescription(String value) {
    description = value;
  }

  /** 读取购买时保存的实物图路径。 */
  public String getImageUrl() {
    return imageUrl;
  }

  /** 写入付款时复制的图路径。 */
  public void setImageUrl(String value) {
    imageUrl = value;
  }

  /** 识别数码店改版前的种子订单；有购买时 SKU 的新订单不因名称相同而被标为旧记录。 */
  public boolean isLegacyDemoItem() {
    return (sku == null || sku.isBlank())
        && productName != null
        && LEGACY_DEMO_PRODUCT_NAMES.contains(productName);
  }

}
