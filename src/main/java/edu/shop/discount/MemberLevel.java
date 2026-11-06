package edu.shop.discount;

import java.math.BigDecimal;

/** 会员等级枚举。等级由扣除退货后的累计消费决定，积分余额不影响等级。 */
public enum MemberLevel {
  NORMAL("普通会员", "0", "1.00"),
  SILVER("银卡会员", "5000", "0.98"),
  GOLD("金卡会员", "15000", "0.95"),
  BLACK("黑金会员", "30000", "0.90");

  private final String displayName;
  private final BigDecimal threshold;
  private final BigDecimal discountRate;

  /** 枚举构造方法：为每种等级保存门槛、中文名和折扣率。 */
  MemberLevel(String displayName, String threshold, String discountRate) {
    this.displayName = displayName;
    this.threshold = new BigDecimal(threshold);
    this.discountRate = new BigDecimal(discountRate);
  }

  /** 获取页面显示的中文等级名。 */
  public String getDisplayName() {
    return displayName;
  }

  /** 获取折扣率；0.95 表示九五折。 */
  public BigDecimal getDiscountRate() {
    return discountRate;
  }

  /** 静态方法：从高到低比较消费门槛，找到当前适用等级。 */
  public static MemberLevel fromTotalSpent(BigDecimal totalSpent) {
    BigDecimal amount = totalSpent == null ? BigDecimal.ZERO : totalSpent;
    MemberLevel[] levels = values();
    for (int i = levels.length - 1; i >= 0; i--) {
      if (amount.compareTo(levels[i].threshold) >= 0) return levels[i];
    }
    return NORMAL;
  }
}
