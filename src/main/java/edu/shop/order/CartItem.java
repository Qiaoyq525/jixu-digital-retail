package edu.shop.order;

/** 数据库查询映射对象：通过 getter/setter 读写，不包含密码字段。 */
public class CartItem {
  private Long id;
  private Long productId;
  private String name;
  private java.math.BigDecimal price;
  private Integer stock;
  private Boolean onSale;
  private String emoji;
  private Integer quantity;

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

  /** 获取 productId 字段。 */
  public Long getProductId() {
    return productId;
  }

  /** 设置 productId 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setProductId(Long productId) {
    this.productId = productId;
  }

  /** 获取 name 字段。 */
  public String getName() {
    return name;
  }

  /** 设置 name 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setName(String name) {
    this.name = name;
  }

  /** 获取 price 字段。 */
  public java.math.BigDecimal getPrice() {
    return price;
  }

  /** 设置 price 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setPrice(java.math.BigDecimal price) {
    this.price = price;
  }

  /** 获取 stock 字段。 */
  public Integer getStock() {
    return stock;
  }

  /** 设置 stock 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setStock(Integer stock) {
    this.stock = stock;
  }

  /** 获取 onSale 字段。 */
  public Boolean getOnSale() {
    return onSale;
  }

  /** 设置 onSale 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setOnSale(Boolean onSale) {
    this.onSale = onSale;
  }

  /** 获取 emoji 字段。 */
  public String getEmoji() {
    return emoji;
  }

  /** 设置 emoji 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setEmoji(String emoji) {
    this.emoji = emoji;
  }

  /** 获取 quantity 字段。 */
  public Integer getQuantity() {
    return quantity;
  }

  /** 设置 quantity 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setQuantity(Integer quantity) {
    this.quantity = quantity;
  }

  /** 计算该行原价小计，不包含结账优惠。 */
  public java.math.BigDecimal getSubtotal() {
    return price.multiply(java.math.BigDecimal.valueOf(quantity)).setScale(2);
  }
  /** 读取商品实时货号，便于搜索与辨认同名商品。 */
  public String getSku() {
    return sku;
  }

  /** MyBatis 从商品关联查询写入货号。 */
  public void setSku(String value) {
    sku = value;
  }

  /** 读取商品实时规格；付款后会复制到订单快照。 */
  public String getDescription() {
    return description;
  }

  /** MyBatis 从商品关联查询写入规格。 */
  public void setDescription(String value) {
    description = value;
  }

  /** 读取购物车缩略图路径。 */
  public String getImageUrl() {
    return imageUrl;
  }

  /** MyBatis 从商品关联查询写入实物图路径。 */
  public void setImageUrl(String value) {
    imageUrl = value;
  }

}
