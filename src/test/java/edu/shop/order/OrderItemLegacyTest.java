package edu.shop.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 旧演示记录识别依赖成交快照，不根据当前商品改名猜测订单来源。 */
class OrderItemLegacyTest {
  @Test
  void recognizesAllMigratedSeedNamesOnlyWithoutASkuSnapshot() {
    for (String name :
        List.of(
            "牧场鲜牛奶", "冷萃拿铁", "白桃乌龙茶", "气泡柠檬水", "黄油可颂", "黑巧曲奇",
            "每日坚果", "帆布小提袋", "陶瓷随手杯", "方格手账本", "极简中性笔", "彩色便利贴")) {
      OrderItem item = item(name, null);
      assertThat(item.isLegacyDemoItem()).as(name + " null SKU").isTrue();
      item.setSku("");
      assertThat(item.isLegacyDemoItem()).as(name + " empty SKU").isTrue();
      item.setSku("   ");
      assertThat(item.isLegacyDemoItem()).as(name + " blank SKU").isTrue();
      item.setSku("CUSTOM-NEW");
      assertThat(item.isLegacyDemoItem()).as(name + " new order").isFalse();
    }
  }

  @Test
  void missingFieldsAndOrdinaryOldDigitalOrdersAreNotClassifiedAsLegacySeeds() {
    assertThat(new OrderItem().isLegacyDemoItem()).isFalse();
    assertThat(item("联想 ThinkBook 14", "").isLegacyDemoItem()).isFalse();
    assertThat(item("用户自建商品", null).isLegacyDemoItem()).isFalse();
  }

  @Test
  void marksTheWholeOrderWhenAnyLineIsALegacySeed() {
    SaleOrder order = new SaleOrder();
    assertThat(order.isLegacyDemoOrder()).isFalse();
    order.setItems(List.of());
    assertThat(order.isLegacyDemoOrder()).isFalse();
    order.setItems(List.of(item("联想 ThinkBook 14", "PC-001")));
    assertThat(order.isLegacyDemoOrder()).isFalse();
    order.setItems(List.of(item("联想 ThinkBook 14", "PC-001"), item("黄油可颂", "")));
    assertThat(order.isLegacyDemoOrder()).isTrue();
  }

  private OrderItem item(String name, String sku) {
    OrderItem item = new OrderItem();
    item.setProductName(name);
    item.setSku(sku);
    return item;
  }
}
