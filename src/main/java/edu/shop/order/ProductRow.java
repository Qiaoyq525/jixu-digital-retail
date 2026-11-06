package edu.shop.order;

/** 数据库查询映射对象：通过 getter/setter 读写，不包含密码字段。 */
public class ProductRow {
  private Long id;
  private String sku;
  private String name;
  private java.math.BigDecimal price;
  private Integer stock;
  private Boolean onSale;
  private String emoji;

  private String description;
  private String imageUrl;
  /** 商品编码用于区分普通商品和隔离的并发实验商品。 */
  public String getSku() {
    return sku;
  }

  public void setSku(String sku) {
    this.sku = sku;
  }

  /** 获取 id 字段。 */
  public Long getId() {
    return id;
  }

  /** 设置 id 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setId(Long id) {
    this.id = id;
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
  /** 读取下单时即将复制进订单的商品规格。 */
  public String getDescription() {
    return description;
  }

  /** MyBatis 写入当前商品规格。 */
  public void setDescription(String value) {
    description = value;
  }

  /** 读取商品实物图路径。 */
  public String getImageUrl() {
    return imageUrl;
  }

  /** MyBatis 写入当前商品实物图路径。 */
  public void setImageUrl(String value) {
    imageUrl = value;
  }

}
