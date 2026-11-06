package edu.shop.discount;

import java.math.BigDecimal;

/** 优惠策略接口：收银代码只依赖接口，各种具体优惠可以独立变化。 */
public interface DiscountPolicy {
  /** 根据上一阶段金额计算本策略执行后的金额。 */
  BigDecimal apply(BigDecimal amount);

  /** 获取此策略的中文说明。 */
  String description();
}
