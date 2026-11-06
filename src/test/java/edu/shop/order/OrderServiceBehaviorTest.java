package edu.shop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import edu.shop.common.BusinessException;
import edu.shop.order.OrderRequests.CheckoutRequest;
import edu.shop.order.OrderRequests.Item;
import edu.shop.order.OrderRequests.PreviewRequest;
import edu.shop.security.CurrentUser;
import edu.shop.user.Role;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** 服务边界测试：角色隔离、请求重试的一致性和会员金额上限。真实回滚另由数据库集成测试验证。 */
class OrderServiceBehaviorTest {
  private CommerceMapper mapper;
  private OrderService service;
  private final CurrentUser admin = new CurrentUser(1L, "admin", "老板", Role.ADMIN);
  private final CurrentUser cashier = new CurrentUser(2L, "cashier1", "销售员", Role.SALES);
  private final CurrentUser member = new CurrentUser(4L, "member1", "会员", Role.MEMBER);

  @BeforeEach
  void setUp() {
    mapper = mock(CommerceMapper.class);
    TransactionTemplate transactions = mock(TransactionTemplate.class);
    when(transactions.execute(any()))
        .thenAnswer(
            invocation -> {
              TransactionCallback<?> action = invocation.getArgument(0);
              return action.doInTransaction(mock(TransactionStatus.class));
            });
    service = new OrderService(mapper, transactions);
  }

  @Test
  void duplicateRequestWithDifferentItemsIsRejected() {
    SaleOrder old = existingOrder();
    when(mapper.byRequestKey("same-key")).thenReturn(old);
    when(mapper.items(10L)).thenReturn(List.of(orderItem(7L, 1)));
    assertThatThrownBy(
            () ->
                service.checkoutFor(
                    cashier, new CheckoutRequest(null, List.of(new Item(7L, 2)), "same-key")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("其他商品或数量");
    verify(mapper, never()).insertOrder(any());
    verify(mapper, never()).decreaseStock(anyLong(), anyInt());
  }

  @Test
  void exactRetryReturnsOriginalOrderWithoutDeductingAgain() {
    when(mapper.byRequestKey("same-key")).thenReturn(existingOrder());
    when(mapper.items(10L)).thenReturn(List.of(orderItem(7L, 3)));
    var result =
        service.checkoutFor(
            cashier,
            new CheckoutRequest(null, List.of(new Item(7L, 1), new Item(7L, 2)), "same-key"));
    assertThat(result.getId()).isEqualTo(10L);
    assertThat(result.getRequestKey()).isNull();
    verify(mapper, never()).insertOrder(any());
    verify(mapper, never()).decreaseStock(anyLong(), anyInt());
  }

  @Test
  void sameKeyOwnedByOtherCashierIsRejectedBeforeReturningDetails() {
    SaleOrder old = existingOrder();
    old.setCashierId(3L);
    when(mapper.byRequestKey("same-key")).thenReturn(old);
    assertThatThrownBy(
            () ->
                service.checkoutFor(
                    cashier, new CheckoutRequest(null, List.of(new Item(7L, 1)), "same-key")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("其他结账");
    verify(mapper, never()).items(anyLong());
  }

  @Test
  void memberCannotUseStaffCheckoutOrViewOtherMembersOrder() {
    assertThatThrownBy(
            () ->
                service.checkoutFor(
                    member, new CheckoutRequest(null, List.of(new Item(7L, 1)), "new-key")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("店员");
    SaleOrder order = existingOrder();
    order.setMemberId(5L);
    when(mapper.order(10L)).thenReturn(order);
    assertThatThrownBy(() -> service.getFor(10L, member))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("无权查看");
  }

  @Test
  void memberPreviewAlwaysUsesLoggedInMember() {
    when(mapper.member(4L)).thenReturn(validMember(4L, 0));
    when(mapper.product(7L)).thenReturn(product(7L, 5, "12.50"));
    var result = service.preview(member, new PreviewRequest(999L, List.of(new Item(7L, 2))));
    assertThat(result.payableAmount()).isEqualByComparingTo("25.00");
    verify(mapper, never()).member(999L);
  }

  @Test
  void reservesInternalRequestNamespacesForServerTasks() {
    assertThatThrownBy(
            () ->
                service.checkoutFor(
                    cashier, new CheckoutRequest(null, List.of(new Item(7L, 1)), "queue:22")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("保留");
    verifyNoInteractions(mapper);
  }

  @Test
  void rejectsPointBalanceOverflowBeforeCreatingOrder() {
    when(mapper.lockMember(4L)).thenReturn(validMember(4L, Integer.MAX_VALUE));
    when(mapper.lockProduct(7L)).thenReturn(product(7L, 5, "12.50"));
    assertThatThrownBy(
            () ->
                service.checkoutFor(
                    cashier, new CheckoutRequest(4L, List.of(new Item(7L, 1)), "new-key")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("积分或累计消费");
    verify(mapper, never()).insertOrder(any());
  }

  @Test
  void rejectsOrderAmountOutsideDatabaseRange() {
    when(mapper.product(7L)).thenReturn(product(7L, 999, "9999999999.99"));
    assertThatThrownBy(
            () -> service.preview(cashier, new PreviewRequest(null, List.of(new Item(7L, 2)))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("订单金额");
  }

  @Test
  void cashierCannotInvokeRefundInternalsEvenForOwnOrder() {
    assertThatThrownBy(() -> service.refundFor(cashier, 10L, "尝试直接退款"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("仅老板")
        .satisfies(error -> assertThat(((BusinessException) error).getStatus()).isEqualTo(403));
    verifyNoInteractions(mapper);
  }

  @Test
  void repeatedRefundDoesNotRestoreStockAgain() {
    SaleOrder refunded = existingOrder();
    refunded.setStatus("REFUNDED");
    when(mapper.order(10L)).thenReturn(refunded);
    when(mapper.lockOrder(10L)).thenReturn(refunded);
    when(mapper.items(10L)).thenReturn(List.of(orderItem(7L, 1)));
    when(mapper.lockProduct(7L)).thenReturn(product(7L, 5, "10"));
    assertThatThrownBy(() -> service.refundFor(admin, 10L, "顾客退货"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("重复退款");
    verify(mapper, never()).restoreStock(anyLong(), anyInt());
  }

  @Test
  void refundRejectsStockAboveSupportedRange() {
    when(mapper.order(10L)).thenReturn(existingOrder());
    when(mapper.items(10L)).thenReturn(List.of(orderItem(7L, 1)));
    when(mapper.lockProduct(7L)).thenReturn(product(7L, 1_000_000, "10"));
    assertThatThrownBy(() -> service.refundFor(admin, 10L, "顾客退货"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("100万");
    verify(mapper, never()).markRefunded(anyLong());
  }

  @Test
  void legacyDemoRefundIsRejectedBeforeAnyInventoryOrOrderWrite() {
    when(mapper.order(10L)).thenReturn(existingOrder());
    OrderItem oldItem = orderItem(7L, 1);
    oldItem.setProductName("黄油可颂");
    oldItem.setSku("");
    when(mapper.items(10L)).thenReturn(List.of(oldItem));

    assertThatThrownBy(() -> service.refundFor(admin, 10L, "尝试退旧演示订单"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("改版前")
        .satisfies(error -> assertThat(((BusinessException) error).getStatus()).isEqualTo(409));

    verify(mapper).order(10L);
    verify(mapper).items(10L);
    verifyNoMoreInteractions(mapper);
  }

  private SaleOrder existingOrder() {
    SaleOrder order = new SaleOrder();
    order.setId(10L);
    order.setCashierId(2L);
    order.setRequestKey("same-key");
    order.setStatus("PAID");
    order.setRefundStatus("PENDING");
    return order;
  }

  private OrderItem orderItem(long id, int quantity) {
    OrderItem item = new OrderItem();
    item.setProductId(id);
    item.setQuantity(quantity);
    return item;
  }

  private MemberRow validMember(long id, int points) {
    MemberRow row = new MemberRow();
    row.setId(id);
    row.setRole("MEMBER");
    row.setEnabled(true);
    row.setPoints(points);
    row.setTotalSpent(BigDecimal.ZERO);
    return row;
  }

  private ProductRow product(long id, int stock, String price) {
    ProductRow row = new ProductRow();
    row.setId(id);
    row.setName("测试商品");
    row.setStock(stock);
    row.setPrice(new BigDecimal(price));
    row.setOnSale(true);
    return row;
  }
}
