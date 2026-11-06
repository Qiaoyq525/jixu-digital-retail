package edu.shop.order;

/** 数据库查询映射对象：通过 getter/setter 读写，不包含密码字段。 */
public class MemberRow {
  private Long id;
  private String username;
  private String displayName;
  private String role;
  private Integer points;
  private java.math.BigDecimal totalSpent;
  private Boolean enabled;

  /** 获取 id 字段。 */
  public Long getId() {
    return id;
  }

  /** 设置 id 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setId(Long id) {
    this.id = id;
  }

  /** 获取 username 字段。 */
  public String getUsername() {
    return username;
  }

  /** 设置 username 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setUsername(String username) {
    this.username = username;
  }

  /** 获取 displayName 字段。 */
  public String getDisplayName() {
    return displayName;
  }

  /** 设置 displayName 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  /** 获取 role 字段。 */
  public String getRole() {
    return role;
  }

  /** 设置 role 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setRole(String role) {
    this.role = role;
  }

  /** 获取 points 字段。 */
  public Integer getPoints() {
    return points;
  }

  /** 设置 points 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setPoints(Integer points) {
    this.points = points;
  }

  /** 获取 totalSpent 字段。 */
  public java.math.BigDecimal getTotalSpent() {
    return totalSpent;
  }

  /** 设置 totalSpent 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setTotalSpent(java.math.BigDecimal totalSpent) {
    this.totalSpent = totalSpent;
  }

  /** 获取 enabled 字段。 */
  public Boolean getEnabled() {
    return enabled;
  }

  /** 设置 enabled 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }
}
