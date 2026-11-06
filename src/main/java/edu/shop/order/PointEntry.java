package edu.shop.order;

/** 数据库查询映射对象：通过 getter/setter 读写，不包含密码字段。 */
public class PointEntry {
  private Long id;
  private Integer changePoints;
  private Integer balanceAfter;
  private Long orderId;
  private String type;
  private java.time.LocalDateTime createdAt;

  /** 获取 id 字段。 */
  public Long getId() {
    return id;
  }

  /** 设置 id 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setId(Long id) {
    this.id = id;
  }

  /** 获取 changePoints 字段。 */
  public Integer getChangePoints() {
    return changePoints;
  }

  /** 设置 changePoints 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setChangePoints(Integer changePoints) {
    this.changePoints = changePoints;
  }

  /** 获取 balanceAfter 字段。 */
  public Integer getBalanceAfter() {
    return balanceAfter;
  }

  /** 设置 balanceAfter 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setBalanceAfter(Integer balanceAfter) {
    this.balanceAfter = balanceAfter;
  }

  /** 获取 orderId 字段。 */
  public Long getOrderId() {
    return orderId;
  }

  /** 设置 orderId 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setOrderId(Long orderId) {
    this.orderId = orderId;
  }

  /** 获取 type 字段。 */
  public String getType() {
    return type;
  }

  /** 设置 type 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setType(String type) {
    this.type = type;
  }

  /** 获取 createdAt 字段。 */
  public java.time.LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /** 设置 createdAt 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setCreatedAt(java.time.LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
