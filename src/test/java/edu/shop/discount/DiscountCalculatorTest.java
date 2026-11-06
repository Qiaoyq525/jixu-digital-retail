package edu.shop.discount;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 金额测试关注边界、先后顺序、积分和等级，并不照抄实现。 */
class DiscountCalculatorTest {
  private final DiscountCalculator calculator = new DiscountCalculator();

  @Test
  void appliesProductThenMemberThenThreshold() {
    var result =
        calculator.calculate(
            new BigDecimal("120"),
            new BigDecimal("0.90"),
            MemberLevel.GOLD,
            new BigDecimal("100"),
            new BigDecimal("10"),
            true);
    assertThat(result.originalAmount()).isEqualByComparingTo("120.00");
    assertThat(result.productDiscount()).isEqualByComparingTo("12.00");
    assertThat(result.memberDiscount()).isEqualByComparingTo("5.40");
    assertThat(result.fullReduction()).isEqualByComparingTo("10.00");
    assertThat(result.payableAmount()).isEqualByComparingTo("92.60");
    assertThat(result.earnedPoints()).isEqualTo(92);
  }

  @Test
  void reductionThresholdUsesDiscountedAmount() {
    var result =
        calculator.calculate(
            new BigDecimal("100"),
            BigDecimal.ONE,
            MemberLevel.GOLD,
            new BigDecimal("100"),
            new BigDecimal("10"),
            true);
    assertThat(result.fullReduction()).isEqualByComparingTo("0");
    assertThat(result.payableAmount()).isEqualByComparingTo("95");
  }

  @Test
  void reductionNeverMakesPriceNegativeAndGuestNeverEarnsPoints() {
    var result =
        calculator.calculate(
            new BigDecimal("5"),
            BigDecimal.ONE,
            MemberLevel.NORMAL,
            new BigDecimal("1"),
            new BigDecimal("10"),
            false);
    assertThat(result.payableAmount()).isEqualByComparingTo("0");
    assertThat(result.fullReduction()).isEqualByComparingTo("5");
    assertThat(result.earnedPoints()).isZero();
  }

  @Test
  void determinesLevelAtExactThresholdAndAfterRefund() {
    assertThat(MemberLevel.fromTotalSpent(null)).isEqualTo(MemberLevel.NORMAL);
    assertThat(MemberLevel.fromTotalSpent(new BigDecimal("4999.99"))).isEqualTo(MemberLevel.NORMAL);
    assertThat(MemberLevel.fromTotalSpent(new BigDecimal("5000.00"))).isEqualTo(MemberLevel.SILVER);
    assertThat(MemberLevel.fromTotalSpent(new BigDecimal("14999.99"))).isEqualTo(MemberLevel.SILVER);
    assertThat(MemberLevel.fromTotalSpent(new BigDecimal("15000"))).isEqualTo(MemberLevel.GOLD);
    assertThat(MemberLevel.fromTotalSpent(new BigDecimal("30000"))).isEqualTo(MemberLevel.BLACK);
    assertThat(MemberLevel.fromTotalSpent(new BigDecimal("29999.99"))).isEqualTo(MemberLevel.GOLD);
  }

  @Test
  void rejectsInvalidRateAndThreshold() {
    assertThatThrownBy(() -> new RateDiscountPolicy(new BigDecimal("1.1"), "错误"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FullReductionPolicy(BigDecimal.ZERO, BigDecimal.TEN))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void guestDoesNotReceiveMemberRateEvenIfCallerPassesGold() {
    var result =
        calculator.calculate(
            new BigDecimal("80"),
            BigDecimal.ONE,
            MemberLevel.GOLD,
            new BigDecimal("100"),
            new BigDecimal("10"),
            false);
    assertThat(result.payableAmount()).isEqualByComparingTo("80.00");
    assertThat(result.memberDiscount()).isEqualByComparingTo("0.00");
    assertThat(result.earnedPoints()).isZero();
  }

  @Test
  void roundsEachDiscountStageAndRejectsNegativeAmount() {
    var result =
        calculator.calculate(
            new BigDecimal("10.01"),
            new BigDecimal("0.95"),
            MemberLevel.GOLD,
            new BigDecimal("100"),
            BigDecimal.ZERO,
            true);
    assertThat(result.productDiscount()).isEqualByComparingTo("0.50");
    assertThat(result.memberDiscount()).isEqualByComparingTo("0.48");
    assertThat(result.payableAmount()).isEqualByComparingTo("9.03");
    assertThat(result.earnedPoints()).isEqualTo(9);
    assertThatThrownBy(
            () ->
                calculator.calculate(
                    new BigDecimal("-1"),
                    BigDecimal.ONE,
                    MemberLevel.NORMAL,
                    BigDecimal.TEN,
                    BigDecimal.ONE,
                    true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RateDiscountPolicy(BigDecimal.ONE, "普通").apply(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reportsPointOverflowBeforeConvertingToInteger() {
    assertThatThrownBy(
            () ->
                calculator.calculate(
                    new BigDecimal("2147483648"),
                    BigDecimal.ONE,
                    MemberLevel.NORMAL,
                    new BigDecimal("9999999999"),
                    BigDecimal.ZERO,
                    true))
        .isInstanceOf(edu.shop.common.BusinessException.class)
        .hasMessageContaining("积分");
  }
}
