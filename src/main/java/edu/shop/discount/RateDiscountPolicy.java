package edu.shop.discount;

import java.math.BigDecimal;

/** 普通优惠与会员折扣都属于按比例打折，可复用同一个具体策略类。 */
public final class RateDiscountPolicy extends AbstractDiscountPolicy {
  private final BigDecimal rate;
  private final String label;

  /** 构造方法：保存折扣率和对应说明。 */
  public RateDiscountPolicy(BigDecimal rate, String label) {
    if (rate == null || rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) > 0) {
      throw new IllegalArgumentException("折扣率必须大于 0 且不超过 1");
    }
    this.rate = rate;
    this.label = label;
  }

  /** 重写抽象方法：金额乘以折扣率。 */
  @Override
  protected BigDecimal calculate(BigDecimal amount) {
    return amount.multiply(rate);
  }

  /** 获取策略描述。 */
  @Override
  public String description() {
    return label + " × " + rate;
  }
}
