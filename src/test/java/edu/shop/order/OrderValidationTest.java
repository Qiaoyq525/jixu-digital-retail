package edu.shop.order;

import static org.assertj.core.api.Assertions.*;

import edu.shop.common.BusinessException;
import edu.shop.order.OrderRequests.Item;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 输入防线测试：库存服务不会接受负数、重复合并后超量或空订单。 */
class OrderValidationTest {
  private final OrderService orders = new OrderService(null, null);

  @Test
  void mergesRepeatedProductsAndSortsForConsistentLockOrder() {
    var result = orders.normalize(List.of(new Item(5L, 2), new Item(2L, 1), new Item(5L, 3)));
    assertThat(result.keySet()).containsExactly(2L, 5L);
    assertThat(result.get(5L)).isEqualTo(5);
  }

  @Test
  void rejectsZeroAndMergedOverLimit() {
    assertThatThrownBy(() -> orders.normalize(List.of(new Item(1L, 0))))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> orders.normalize(List.of(new Item(1L, 600), new Item(1L, 600))))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> orders.normalize(List.of())).isInstanceOf(BusinessException.class);
  }
}
