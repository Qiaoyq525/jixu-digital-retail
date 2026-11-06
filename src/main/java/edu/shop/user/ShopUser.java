package edu.shop.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 数据库用户对象。密码哈希仅用于登录验证，JsonIgnore 防止序列化到浏览器。 */
public class ShopUser {
  private Long id;
  private String username;
  @JsonIgnore private String passwordHash;
  private String displayName;
  private Role role;
  private int points;
  private BigDecimal totalSpent;
  private boolean enabled;
  private LocalDateTime createdAt;

  /** 读取数据库编号。 */
  public Long getId() {
    return id;
  }

  /** MyBatis 将主键或查询结果写入对象。 */
  public void setId(Long id) {
    this.id = id;
  }

  /** 读取登录名。 */
  public String getUsername() {
    return username;
  }

  /** 设置登录名。 */
  public void setUsername(String username) {
    this.username = username;
  }

  /** 登录服务读取 BCrypt 哈希，禁止输出原始密码。 */
  public String getPasswordHash() {
    return passwordHash;
  }

  /** 保存已计算的哈希。 */
  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  /** 读取页面显示昵称。 */
  public String getDisplayName() {
    return displayName;
  }

  /** 设置昵称。 */
  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  /** 读取角色枚举。 */
  public Role getRole() {
    return role;
  }

  /** 设置角色。 */
  public void setRole(Role role) {
    this.role = role;
  }

  /** 读取积分余额。 */
  public int getPoints() {
    return points;
  }

  /** 设置积分余额。 */
  public void setPoints(int points) {
    this.points = points;
  }

  /** 读取退款后净累计消费。 */
  public BigDecimal getTotalSpent() {
    return totalSpent;
  }

  /** 设置累计消费。 */
  public void setTotalSpent(BigDecimal totalSpent) {
    this.totalSpent = totalSpent;
  }

  /** 读取是否允许登录。 */
  public boolean isEnabled() {
    return enabled;
  }

  /** 启用或禁用用户。 */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /** 读取注册时间。 */
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /** MyBatis 写入时间。 */
  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
