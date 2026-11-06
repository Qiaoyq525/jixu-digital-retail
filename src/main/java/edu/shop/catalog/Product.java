package edu.shop.catalog;

import java.math.BigDecimal;

/** 商品持久化对象；库存的实际修改只能通过采购、付款或退款业务进行。 */
public class Product {
  private Long id;
  private Long categoryId;
  private String categoryName;
  private String sku;
  private String name;
  private String description;
  private BigDecimal price;
  private int stock;
  private int lowStockThreshold;
  private boolean onSale;
  private String imageUrl;
  private String emoji;
  /** 读取商品主键。 */
  public Long getId() {
    return id;
  }

  /** 写入数据库主键。 */
  public void setId(Long id) {
    this.id = id;
  }

  /** 读取分类编号。 */
  public Long getCategoryId() {
    return categoryId;
  }

  /** 设置分类编号。 */
  public void setCategoryId(Long value) {
    categoryId = value;
  }

  /** 读取关联查询的分类名称。 */
  public String getCategoryName() {
    return categoryName;
  }

  /** 写入分类名称。 */
  public void setCategoryName(String value) {
    categoryName = value;
  }

  /** 读取货号。 */
  public String getSku() {
    return sku;
  }

  /** 设置货号。 */
  public void setSku(String value) {
    sku = value;
  }

  /** 读取商品名。 */
  public String getName() {
    return name;
  }

  /** 设置商品名。 */
  public void setName(String value) {
    name = value;
  }

  /** 读取介绍。 */
  public String getDescription() {
    return description;
  }

  /** 设置介绍。 */
  public void setDescription(String value) {
    description = value;
  }

  /** 读取精确到分的单价。 */
  public BigDecimal getPrice() {
    return price;
  }

  /** 设置单价。 */
  public void setPrice(BigDecimal value) {
    price = value;
  }

  /** 读取当前库存。 */
  public int getStock() {
    return stock;
  }

  /** MyBatis写入库存。 */
  public void setStock(int value) {
    stock = value;
  }

  /** 读取低库存阈值。 */
  public int getLowStockThreshold() {
    return lowStockThreshold;
  }

  /** 设置提醒阈值。 */
  public void setLowStockThreshold(int value) {
    lowStockThreshold = value;
  }

  /** 判断上架状态。 */
  public boolean isOnSale() {
    return onSale;
  }

  /** 设置上架状态。 */
  public void setOnSale(boolean value) {
    onSale = value;
  }

  /** 读取展示图标。 */
  public String getEmoji() {
    return emoji;
  }

  /** 设置图标。 */
  public void setEmoji(String value) {
    emoji = value;
  }
  /** 读取本地商品图路径；空字符串表示尚未配置实物图。 */
  public String getImageUrl() {
    return imageUrl;
  }

  /** 设置图片路径；购物车展示实时路径，订单则保存购买时的副本。 */
  public void setImageUrl(String value) {
    imageUrl = value;
  }
}
