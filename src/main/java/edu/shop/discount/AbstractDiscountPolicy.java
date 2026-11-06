package edu.shop.discount;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 抽象优惠类封装所有策略共有的金额规范；具体计算由子类重写。 */
public abstract class AbstractDiscountPolicy implements DiscountPolicy {
  /** 模板方法：统一非负金额、四舍五入到两位小数，避免各策略出现不同精度。 */
  @Override
  public final BigDecimal apply(BigDecimal amount) {
    if (amount == null || amount.signum() < 0) {
      throw new IllegalArgumentException("参与优惠的金额不能为空或为负数");
    }
    return calculate(amount).max(BigDecimal.ZERO).min(amount).setScale(2, RoundingMode.HALF_UP);
  }

  /** 抽象方法：由子类完成打折或者满减计算。 */
  protected abstract BigDecimal calculate(BigDecimal amount);
}
