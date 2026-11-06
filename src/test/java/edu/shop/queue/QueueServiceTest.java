package edu.shop.queue;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.shop.common.BusinessException;
import edu.shop.order.*;
import edu.shop.security.CurrentUser;
import edu.shop.user.Role;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** 手动收银边界：提交不付款、固定柜台归属、最短队列、权限与幂等重试。 */
class QueueServiceTest {
  private QueueMapper mapper;
  private CommerceMapper commerce;
  private OrderService orders;
  private QueueService service;
  private final CurrentUser member = new CurrentUser(4L, "member1", "会员一", Role.MEMBER);
  private final CurrentUser cashier = new CurrentUser(2L, "cashier1", "小周", Role.SALES);

  @BeforeEach
  void setUp() {
    mapper = mock(QueueMapper.class);
    commerce = mock(CommerceMapper.class);
    orders = mock(OrderService.class);
    TransactionTemplate transactions = mock(TransactionTemplate.class);
    when(transactions.execute(any())).thenAnswer(invocation -> {
      TransactionCallback<?> action = invocation.getArgument(0);
      return action.doInTransaction(mock(TransactionStatus.class));
    });
    doAnswer(invocation -> {
      Consumer<TransactionStatus> action = invocation.getArgument(0);
      action.accept(mock(TransactionStatus.class)); return null;
    }).when(transactions).executeWithoutResult(any());
    service = new QueueService(mapper, commerce, orders, new ObjectMapper(), transactions);
    when(commerce.enabledCashiers()).thenReturn(List.of(2L, 3L));
    MemberRow row = user(4L, "会员一", "member1", "MEMBER");
    when(commerce.lockMember(4L)).thenReturn(row);
    when(commerce.member(4L)).thenReturn(row);
    when(commerce.member(2L)).thenReturn(user(2L, "小周", "cashier1", "SALES"));
    when(commerce.member(3L)).thenReturn(user(3L, "小陈", "cashier2", "SALES"));
    when(mapper.lockCounters()).thenReturn(List.of(counter(1, 2L, 0), counter(2, 3L, 0)));
    when(mapper.counters()).thenReturn(List.of(counter(1, 2L, 0), counter(2, 3L, 0)));
    when(mapper.lockCounter(1)).thenReturn(counter(1, 2L, 0));
    when(orders.preview(any(), any())).thenReturn(new OrderPreview(BigDecimal.TEN, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, 10, "", List.of()));
  }

  @Test
  void joinPersistsSnapshotAndNamedCounterWithoutTakingPayment() {
    CartItem cart = new CartItem(); cart.setProductId(7L); cart.setQuantity(2);
    when(commerce.cart(4L)).thenReturn(List.of(cart));
    CheckoutTicket saved = ticket(11L, 4L, "WAITING");
    doAnswer(invocation -> { ((CheckoutTicket) invocation.getArgument(0)).setId(11L); return 1; })
        .when(mapper).insert(any());
    when(mapper.ticket(11L)).thenReturn(saved);
    var result = service.join(member, "ticket-key");
    ArgumentCaptor<CheckoutTicket> captor = ArgumentCaptor.forClass(CheckoutTicket.class);
    verify(mapper).insert(captor.capture());
    assertThat(captor.getValue().getPayloadJson()).isEqualTo("[{\"productId\":7,\"quantity\":2}]");
    assertThat(captor.getValue().getCounterNo()).isEqualTo(1);
    assertThat(captor.getValue().getCashierId()).isEqualTo(2L);
    assertThat(result).containsEntry("status", "WAITING").containsEntry("cashierName", "小周")
        .containsEntry("cashierUsername", "cashier1").containsEntry("counterNo", 1);
    assertThat(result).doesNotContainKeys("payloadJson", "requestKey");
    verify(orders, never()).checkoutQueued(anyLong(), anyLong(), any(), anyString());
  }

  @Test
  void shortestQueueIsChosenWithoutRenumberingDisabledCashier() {
    CheckoutCounter first = counter(1, 2L, 2);
    CheckoutCounter second = counter(2, 3L, 0);
    when(mapper.counters()).thenReturn(List.of(first, second));
    when(mapper.ticket(11L)).thenReturn(ticket(11L, 4L, "WAITING"));
    doAnswer(invocation -> { CheckoutTicket t = invocation.getArgument(0); t.setId(11L);
      assertThat(t.getCounterNo()).isEqualTo(2); assertThat(t.getCashierId()).isEqualTo(3L); return 1; })
        .when(mapper).insert(any());
    service.join(member, "shortest");
    first.setWaitingCount(0); first.setCashierEnabled(false);
    service.join(member, "disabled");
    verify(mapper, never()).assignCounter(anyInt(), anyLong());
  }

  @Test
  void repeatedKeyReturnsExistingTicketEvenWhenCountersUnavailable() {
    CheckoutTicket saved = ticket(11L, 4L, "COMPLETED"); saved.setOrderId(20L);
    when(mapper.byKey("ticket-key")).thenReturn(saved);
    when(mapper.ticket(11L)).thenReturn(saved);
    when(mapper.counters()).thenReturn(List.of());
    assertThat(service.join(member, "ticket-key")).containsEntry("orderId", 20L);
    verify(mapper, never()).insert(any()); verify(commerce, never()).cart(4L);
  }

  @Test
  void sameKeyFromAnotherMemberCannotReadTheirTicket() {
    when(mapper.byKey("ticket-key")).thenReturn(ticket(11L, 5L, "COMPLETED"));
    assertThatThrownBy(() -> service.join(member, "ticket-key")).isInstanceOf(BusinessException.class)
        .hasMessageContaining("无权操作");
    verify(mapper, never()).ticket(11L);
  }

  @Test
  void activeTicketCapacityAndUnavailableCashierPreventJoining() {
    when(mapper.activeCount(4L)).thenReturn(1);
    assertThatThrownBy(() -> service.join(member, "new-key")).hasMessageContaining("正在排队");
    when(mapper.activeCount(4L)).thenReturn(0);
    when(mapper.counters()).thenReturn(List.of(counter(1, 2L, 200)));
    assertThatThrownBy(() -> service.join(member, "new-key")).hasMessageContaining("人数已满");
    when(mapper.counters()).thenReturn(List.of());
    assertThatThrownBy(() -> service.join(member, "new-key")).hasMessageContaining("没有启用的收银员");
    verify(mapper, never()).insert(any());
  }

  @Test
  void cancelRejectsAlreadyClaimedAndForeignOrMissingTicket() {
    when(mapper.ticket(11L)).thenReturn(ticket(11L, 4L, "PROCESSING"));
    assertThatThrownBy(() -> service.cancel(member, 11L)).hasMessageContaining("不能取消");
    verify(mapper).cancel(11L, 4L);
    when(mapper.ticket(12L)).thenReturn(ticket(12L, 5L, "WAITING"));
    assertThatThrownBy(() -> service.cancel(member, 12L)).hasMessageContaining("无权操作");
    assertThatThrownBy(() -> service.cancel(member, 99L)).hasMessageContaining("不存在");
    verify(mapper, never()).cancel(eq(12L), anyLong());
  }

  @Test
  void snapshotMasksMemberTicketIdsAndFiltersSalesTickets() {
    CheckoutCounter first = counter(1, 2L, 1); first.setTicketId(999L);
    CheckoutCounter third = counter(3, null, 0);
    when(mapper.counters()).thenReturn(List.of(first, counter(2, 3L, 0), third));
    when(mapper.list(4L, null)).thenReturn(List.of(ticket(11L, 4L, "WAITING")));
    Map<String, Object> result = service.snapshot(member);
    @SuppressWarnings("unchecked") List<Map<String, Object>> counters = (List<Map<String, Object>>) result.get("counters");
    assertThat(counters.getFirst()).containsEntry("status", "WAITING").containsEntry("ticketId", null)
        .containsEntry("cashierName", "小周");
    assertThat(counters.get(2)).containsEntry("status", "CLOSED");
    assertThat(result).containsEntry("waitingCount", 1L);
    service.snapshot(cashier); verify(mapper).list(null, 2L);
    service.snapshot(new CurrentUser(1L, "admin", "老板", Role.ADMIN)); verify(mapper).list(null, null);
  }

  @Test
  void onlyMembersCanJoinOrCancelAndRequestKeyMustBeValid() {
    assertThatThrownBy(() -> service.join(cashier, "key")).hasMessageContaining("仅会员");
    assertThatThrownBy(() -> service.cancel(cashier, 11L)).hasMessageContaining("仅会员");
    assertThatThrownBy(() -> service.join(member, " ")).hasMessageContaining("请求编号");
    assertThatThrownBy(() -> service.join(member, "k".repeat(101))).hasMessageContaining("请求编号");
  }

  @Test
  void startupBindsOnlyEmptyCountersThenReconcilesPaidBeforeInterrupted() {
    when(mapper.lockCounters()).thenReturn(List.of(counter(1, 2L, 0), counter(2, null, 0)));
    service.start(); verify(mapper).assignCounter(2, 3L);
    var order = inOrder(mapper); order.verify(mapper).recoverPaid(); order.verify(mapper).recoverInterrupted();
    verifyNoInteractions(orders);
  }

  @Test
  void confirmationRequiresAssignedEnabledCashierAndFirstPosition() {
    when(mapper.ticket(11L)).thenReturn(ticket(11L, 4L, "WAITING"));
    when(mapper.lockTicket(11L)).thenReturn(ticket(11L, 4L, "WAITING"));
    assertThatThrownBy(() -> service.confirm(member, 11L)).hasMessageContaining("仅对应收银员");
    assertThatThrownBy(() -> service.confirm(new CurrentUser(3L, "cashier2", "小陈", Role.SALES), 11L))
        .hasMessageContaining("分配的收银员");
    when(mapper.position(1, 11L)).thenReturn(2);
    assertThatThrownBy(() -> service.confirm(cashier, 11L)).hasMessageContaining("排在前面");
    MemberRow disabled = user(2L, "小周", "cashier1", "SALES"); disabled.setEnabled(false);
    when(commerce.member(2L)).thenReturn(disabled);
    assertThatThrownBy(() -> service.confirm(cashier, 11L)).hasMessageContaining("账号不可用");
    verify(orders, never()).checkoutQueued(anyLong(), anyLong(), any(), anyString());
  }

  @Test
  void confirmAtomicallyCompletesAndCompletedRetryNeverPaysAgain() {
    CheckoutTicket active = ticket(11L, 4L, "WAITING");
    when(mapper.ticket(11L)).thenReturn(active);
    when(mapper.lockTicket(11L)).thenReturn(active);
    when(mapper.position(1, 11L)).thenReturn(1);
    when(mapper.claim(11L, 2L, 1)).thenReturn(1);
    when(mapper.complete(11L, 20L)).thenReturn(1);
    SaleOrder order = new SaleOrder(); order.setId(20L);
    when(orders.checkoutQueued(eq(4L), eq(2L), any(), eq("queue:11"))).thenReturn(order);
    service.confirm(cashier, 11L);
    active.setStatus("COMPLETED"); active.setOrderId(20L);
    assertThat(service.confirm(cashier, 11L)).containsEntry("orderId", 20L);
    verify(orders, times(1)).checkoutQueued(eq(4L), eq(2L), any(), eq("queue:11"));
    var sequence = inOrder(mapper, orders);
    sequence.verify(mapper).claim(11L, 2L, 1);
    sequence.verify(orders).checkoutQueued(eq(4L), eq(2L), any(), eq("queue:11"));
    sequence.verify(mapper).complete(11L, 20L);
  }

  @Test
  void invalidSnapshotAppearsAsPreviewErrorInsteadOfBreakingBoard() {
    CheckoutTicket ticket = ticket(11L, 4L, "WAITING"); ticket.setPayloadJson("not-json");
    when(mapper.list(4L, null)).thenReturn(List.of(ticket));
    @SuppressWarnings("unchecked") List<Map<String,Object>> rows = (List<Map<String,Object>>) service.snapshot(member).get("tickets");
    assertThat(rows.getFirst().get("previewError")).asString().contains("重新提交");
  }

  private MemberRow user(long id, String name, String username, String role) {
    MemberRow row = new MemberRow(); row.setId(id); row.setEnabled(true); row.setDisplayName(name);
    row.setUsername(username); row.setRole(role); row.setPoints(0); row.setTotalSpent(BigDecimal.ZERO); return row;
  }

  private CheckoutCounter counter(int no, Long cashierId, int waiting) {
    CheckoutCounter row = new CheckoutCounter(); row.setCounterNo(no); row.setCashierId(cashierId);
    row.setCashierName(no == 1 ? "小周" : "小陈"); row.setCashierUsername("cashier" + no);
    row.setCashierEnabled(true); row.setCashierRole("SALES"); row.setWaitingCount(waiting); row.setProcessingCount(0); return row;
  }

  private CheckoutTicket ticket(long id, long memberId, String status) {
    CheckoutTicket ticket = new CheckoutTicket(); ticket.setId(id); ticket.setMemberId(memberId);
    ticket.setStatus(status); ticket.setCounterNo(1); ticket.setCashierId(2L);
    ticket.setPayloadJson("[{\"productId\":7,\"quantity\":2}]"); return ticket;
  }
}
