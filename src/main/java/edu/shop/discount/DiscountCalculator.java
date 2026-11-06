package edu.shop.discount;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 无数据库依赖的金额计算器，便于单独测试不同优惠组合。 */
public final class DiscountCalculator {
  /** record 是只读数据载体：编译器自动生成构造方法和访问方法。 */
  public record Result(
      BigDecimal originalAmount,
      BigDecimal productDiscount,
      BigDecimal memberDiscount,
      BigDecimal fullReduction,
      BigDecimal payableAmount,
      int earnedPoints,
      String discountDescription) {}

  /** 顺序执行三种接口策略，逐步保留到分，再计算本次赠送积分。 */
  public Result calculate(
      BigDecimal original,
      BigDecimal productRate,
      MemberLevel level,
      BigDecimal threshold,
      BigDecimal reduction,
      boolean member) {
    if (original == null || original.signum() < 0) {
      throw new IllegalArgumentException("订单原价不能为空或为负数");
    }
    BigDecimal base = original.setScale(2, RoundingMode.HALF_UP);
    List<DiscountPolicy> policies =
        DiscountPolicyFactory.create(
            productRate, member ? level : MemberLevel.NORMAL, threshold, reduction);
    BigDecimal afterProduct = policies.get(0).apply(base);
    BigDecimal afterMember = policies.get(1).apply(afterProduct);
    BigDecimal payable = policies.get(2).apply(afterMember);
    BigDecimal wholeAmount = payable.setScale(0, RoundingMode.DOWN);
    if (member && wholeAmount.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
      throw new edu.shop.common.BusinessException("本单积分超出系统支持范围，请分批结账");
    }
    int points = member ? wholeAmount.intValueExact() : 0;
    String description =
        policies.stream().map(DiscountPolicy::description).reduce((a, b) -> a + "；" + b).orElse("");
    return new Result(
        base,
        base.subtract(afterProduct),
        afterProduct.subtract(afterMember),
        afterMember.subtract(payable),
        payable,
        points,
        description);
  }
}
