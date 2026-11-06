package edu.shop.discount;

import java.math.BigDecimal;

/** 满减优惠：达到门槛后减一次；不会因多倍门槛重复满减。 */
public final class FullReductionPolicy extends AbstractDiscountPolicy {
  private final BigDecimal threshold;
  private final BigDecimal reduction;

  /** 构造方法：保存满减门槛和减免金额；减免为 0 表示关闭满减。 */
  public FullReductionPolicy(BigDecimal threshold, BigDecimal reduction) {
    if (threshold == null
        || reduction == null
        || threshold.signum() <= 0
        || reduction.signum() < 0) {
      throw new IllegalArgumentException("满减门槛必须大于 0，减免金额不能为负数");
    }
    this.threshold = threshold;
    this.reduction = reduction;
  }

  /** 重写抽象方法：满足门槛才减免，父类会保证最终金额不小于零。 */
  @Override
  protected BigDecimal calculate(BigDecimal amount) {
    return amount.compareTo(threshold) >= 0 ? amount.subtract(reduction) : amount;
  }

  /** 获取满减说明。 */
  @Override
  public String description() {
    return "折后满 " + threshold + " 减 " + reduction;
  }
}
