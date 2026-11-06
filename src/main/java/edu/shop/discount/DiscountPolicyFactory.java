package edu.shop.discount;

import java.math.BigDecimal;
import java.util.List;

/** 简单工厂：集中创建本次结账的策略对象。 注意：这里只有一个工厂类，没有让工厂子类决定产品类型，因此不把它误称为“工厂方法模式”。 */
public final class DiscountPolicyFactory {
  private DiscountPolicyFactory() {}

  /** 按业务顺序创建全场折扣、会员折扣、满减三个策略；List 的顺序就是执行顺序。 */
  public static List<DiscountPolicy> create(
      BigDecimal productRate, MemberLevel level, BigDecimal threshold, BigDecimal reduction) {
    MemberLevel actualLevel = level == null ? MemberLevel.NORMAL : level;
    return List.of(
        new RateDiscountPolicy(productRate, "全场优惠"),
        new RateDiscountPolicy(actualLevel.getDiscountRate(), actualLevel.getDisplayName()),
        new FullReductionPolicy(threshold, reduction));
  }
}
