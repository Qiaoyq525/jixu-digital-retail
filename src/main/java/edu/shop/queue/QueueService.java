package edu.shop.queue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.shop.common.BusinessException;
import edu.shop.order.*;
import edu.shop.order.OrderRequests.Item;
import edu.shop.security.CurrentUser;
import edu.shop.user.Role;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 会员提交只生成等待票据，不自动付款。柜台固定绑定员工，由该员工主动确认后才调用订单事务。
 * 排队顺序和柜台映射都保存在 MySQL；应用重启不会丢失等待中的提交。
 */
@Service
public class QueueService {
  private final QueueMapper mapper;
  private final CommerceMapper commerce;
  private final OrderService orders;
  private final ObjectMapper json;
  private final TransactionTemplate transactions;

  /** 构造方法注入业务依赖；此服务不创建自动付款线程。 */
  public QueueService(QueueMapper mapper, CommerceMapper commerce, OrderService orders,
      ObjectMapper json, TransactionTemplate transactions) {
    this.mapper = mapper;
    this.commerce = commerce;
    this.orders = orders;
    this.json = json;
    this.transactions = transactions;
  }

  /** 种子账号已生成后填充空柜台，并恢复旧版本中断记录；已分配的 WAITING 保持原样。 */
  @EventListener(ApplicationReadyEvent.class)
  public void start() {
    transactions.executeWithoutResult(status -> {
      bindEmptyCounters(mapper.lockCounters());
      mapper.recoverPaid();
      mapper.recoverInterrupted();
    });
  }

  /** 会员提交时，锁定柜台再锁会员，保存服务器购物车快照并分配到最短队列。 */
  public Map<String, Object> join(CurrentUser actor, String requestKey) {
    requireMember(actor);
    if (requestKey == null || requestKey.isBlank() || requestKey.length() > 100)
      throw new BusinessException("请提供有效的请求编号");
    CheckoutTicket ticket;
    try {
      ticket = transactions.execute(status -> {
        bindEmptyCounters(mapper.lockCounters());
        MemberRow member = commerce.lockMember(actor.id());
        if (member == null || !Boolean.TRUE.equals(member.getEnabled()))
          throw new BusinessException("会员已停用");
        CheckoutTicket old = mapper.byKey(requestKey);
        if (old != null) {
          verifyOwner(old, actor);
          return old;
        }
        if (mapper.activeCount(actor.id()) > 0)
          throw new BusinessException("你已有正在排队的结账，请等待或先取消");
        List<CheckoutCounter> counters = mapper.counters();
        if (counters.stream().mapToInt(this::activeCount).sum() >= 200)
          throw new BusinessException("排队人数已满，请稍后重新提交");
        int count = counterCount();
        CheckoutCounter counter = counters.stream()
            .filter(c -> c.getCounterNo() <= count && available(c))
            .min(Comparator.comparingInt(this::activeCount).thenComparing(CheckoutCounter::getCounterNo))
            .orElseThrow(() -> new BusinessException("当前没有启用的收银员或可接单的收银台，请联系老板"));
        List<Item> items = commerce.cart(actor.id()).stream()
            .map(c -> new Item(c.getProductId(), c.getQuantity())).toList();
        orders.preview(actor, new OrderRequests.PreviewRequest(null, items));
        CheckoutTicket created = new CheckoutTicket();
        created.setMemberId(actor.id());
        created.setCashierId(counter.getCashierId());
        created.setCounterNo(counter.getCounterNo());
        created.setRequestKey(requestKey);
        created.setStatus("WAITING");
        try {
          created.setPayloadJson(json.writeValueAsString(items));
        } catch (Exception failure) {
          throw new BusinessException("购物车快照生成失败，请重试");
        }
        mapper.insert(created);
        commerce.audit(actor.id(), "QUEUE_JOIN", "提交至 " + counter.getCounterNo()
            + " 号收银台，收银员 " + counter.getCashierName() + "，票据 " + created.getId());
        return created;
      });
    } catch (DuplicateKeyException duplicate) {
      ticket = mapper.byKey(requestKey);
      if (ticket == null) throw duplicate;
      verifyOwner(ticket, actor);
    }
    return ticketView(mapper.ticket(ticket.getId()));
  }

  /** 只有指定销售员可以确认自己的首张票据；付款与票据完成在同一个事务中提交。 */
  public Map<String, Object> confirm(CurrentUser actor, long id) {
    if (actor.role() != Role.SALES) throw new BusinessException(403, "仅对应收银员可以确认结账");
    CheckoutTicket initial = requireTicket(mapper.ticket(id));
    verifyCashier(initial, actor);
    transactions.executeWithoutResult(status -> {
      CheckoutCounter counter = initial.getCounterNo() == null ? null : mapper.lockCounter(initial.getCounterNo());
      if (counter == null || !Objects.equals(counter.getCashierId(), actor.id()))
        throw new BusinessException(403, "该票据不属于你的收银台");
      // 所有会员结账先锁会员，再锁票据与商品，保持加锁顺序一致。
      commerce.lockMember(initial.getMemberId());
      CheckoutTicket ticket = requireTicket(mapper.lockTicket(id));
      verifyCashier(ticket, actor);
      MemberRow cashier = commerce.member(actor.id());
      if (cashier == null || !Boolean.TRUE.equals(cashier.getEnabled()) || !"SALES".equals(cashier.getRole()))
        throw new BusinessException(403, "收银员账号不可用，请联系老板");
      if ("COMPLETED".equals(ticket.getStatus())) return; // 网络重试返回原订单，不重复扣库存。
      if (!"WAITING".equals(ticket.getStatus())) throw new BusinessException("票据已取消或已结束，不能确认");
      if (mapper.position(ticket.getCounterNo(), id) != 1)
        throw new BusinessException("请先处理本收银台排在前面的会员");
      if (mapper.claim(id, actor.id(), ticket.getCounterNo()) != 1)
        throw new BusinessException("票据状态已变化，请刷新后重试");
      SaleOrder order = orders.checkoutQueued(ticket.getMemberId(), actor.id(), readItems(ticket), "queue:" + id);
      if (mapper.complete(id, order.getId()) != 1)
        throw new BusinessException("票据状态已变化，本次结账已回滚");
      commerce.audit(actor.id(), "QUEUE_CONFIRM", "在 " + ticket.getCounterNo() + " 号收银台确认票据 " + id);
    });
    return ticketView(mapper.ticket(id));
  }

  /** 看板只暴露允许读取的票据；会员能看柜台姓名，但不能看其他人的票据编号或购物清单。 */
  public Map<String, Object> snapshot(CurrentUser actor) {
    List<CheckoutTicket> tickets = mapper.list(actor.role() == Role.MEMBER ? actor.id() : null,
        actor.role() == Role.SALES ? actor.id() : null);
    int count = counterCount();
    List<Map<String, Object>> counters = new ArrayList<>();
    for (CheckoutCounter row : mapper.counters()) {
      Map<String, Object> counter = new LinkedHashMap<>();
      counter.put("counterNo", row.getCounterNo());
      counter.put("cashierId", row.getCashierId());
      counter.put("cashierName", row.getCashierName());
      counter.put("cashierUsername", row.getCashierUsername());
      String state = row.getCounterNo() > count ? "CLOSED" : row.getCashierId() == null ? "UNASSIGNED"
          : !available(row) ? "UNAVAILABLE" : count(row.getProcessingCount()) > 0 ? "BUSY"
          : count(row.getWaitingCount()) > 0 ? "WAITING" : "IDLE";
      counter.put("status", state);
      counter.put("waitingCount", count(row.getWaitingCount()));
      counter.put("ticketId", actor.role() == Role.MEMBER ? null : row.getTicketId());
      counters.add(counter);
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("counters", counters);
    result.put("tickets", tickets.stream().map(this::ticketView).toList());
    result.put("waitingCount", tickets.stream().filter(t -> "WAITING".equals(t.getStatus())).count());
    result.put("processingCount", tickets.stream().filter(t -> "PROCESSING".equals(t.getStatus())).count());
    return result;
  }

  /** 取消和确认采用同样的锁顺序，只可能有一个结果成功。 */
  public Map<String, Object> cancel(CurrentUser actor, long id) {
    requireMember(actor);
    CheckoutTicket initial = requireTicket(mapper.ticket(id));
    verifyOwner(initial, actor);
    transactions.executeWithoutResult(status -> {
      if (initial.getCounterNo() != null) mapper.lockCounter(initial.getCounterNo());
      commerce.lockMember(actor.id());
      if (mapper.cancel(id, actor.id()) != 1) throw new BusinessException("票据已被柜台受理或已结束，不能取消");
      commerce.audit(actor.id(), "QUEUE_CANCEL", "取消待确认票据 " + id);
    });
    return ticketView(mapper.ticket(id));
  }

  /** 新增的销售员只分配到空柜台；停用账号原来的柜台编号不改变。 */
  private void bindEmptyCounters(List<CheckoutCounter> counters) {
    Set<Long> assigned = new HashSet<>();
    counters.forEach(c -> { if (c.getCashierId() != null) assigned.add(c.getCashierId()); });
    Iterator<Long> freeCashiers = commerce.enabledCashiers().stream().filter(id -> !assigned.contains(id)).iterator();
    for (CheckoutCounter counter : counters) {
      if (counter.getCashierId() == null && freeCashiers.hasNext())
        mapper.assignCounter(counter.getCounterNo(), freeCashiers.next());
    }
  }

  private int counterCount() {
    try {
      String configured = commerce.setting("COUNTER_COUNT");
      return configured == null ? 2 : Math.max(1, Math.min(4, Integer.parseInt(configured)));
    } catch (NumberFormatException invalid) { return 2; }
  }

  private boolean available(CheckoutCounter counter) {
    return counter.getCashierId() != null && Boolean.TRUE.equals(counter.getCashierEnabled())
        && "SALES".equals(counter.getCashierRole());
  }

  private int activeCount(CheckoutCounter counter) {
    return count(counter.getWaitingCount()) + count(counter.getProcessingCount());
  }

  private int count(Integer number) { return number == null ? 0 : number; }

  /** 只发送可展示的 DTO，内部 JSON 与幂等键不会返回浏览器。 */
  private Map<String, Object> ticketView(CheckoutTicket ticket) {
    Map<String, Object> view = new LinkedHashMap<>();
    MemberRow member = commerce.member(ticket.getMemberId());
    MemberRow cashier = ticket.getCashierId() == null ? null : commerce.member(ticket.getCashierId());
    view.put("id", ticket.getId());
    view.put("memberId", ticket.getMemberId());
    view.put("memberName", member == null ? "会员" : member.getDisplayName());
    view.put("status", ticket.getStatus());
    view.put("orderId", ticket.getOrderId());
    view.put("errorMessage", ticket.getErrorMessage());
    view.put("counterNo", ticket.getCounterNo());
    view.put("cashierId", ticket.getCashierId());
    view.put("cashierName", cashier == null ? null : cashier.getDisplayName());
    view.put("cashierUsername", cashier == null ? null : cashier.getUsername());
    view.put("createdAt", ticket.getCreatedAt());
    boolean active = "WAITING".equals(ticket.getStatus()) || "PROCESSING".equals(ticket.getStatus());
    view.put("position", active && ticket.getCounterNo() != null ? mapper.position(ticket.getCounterNo(), ticket.getId()) : null);
    view.put("items", List.of());
    view.put("totalAmount", null);
    view.put("previewError", null);
    if (active) {
      try {
        List<Item> items = readItems(ticket);
        // 即使试算因库存不足失败，收银员仍能看清会员提交的商品及数量。
        view.put("items", items.stream().map(this::currentLine).toList());
        OrderPreview preview = orders.preview(new CurrentUser(ticket.getMemberId(), "", "", Role.MEMBER),
            new OrderRequests.PreviewRequest(null, items));
        view.put("totalAmount", preview.payableAmount());
      } catch (BusinessException failure) { view.put("previewError", failure.getMessage()); }
    } else if (ticket.getOrderId() != null) {
      SaleOrder order = commerce.order(ticket.getOrderId());
      if (order != null) {
        view.put("totalAmount", order.getPayableAmount());
        view.put("items", commerce.items(order.getId()).stream().map(item -> line(item.getProductId(),
            item.getProductName(), item.getSku(), item.getDescription(), item.getImageUrl(),
            item.getQuantity(), item.getUnitPrice(), item.getSubtotal())).toList());
      }
    }
    return view;
  }

  private Map<String, Object> currentLine(Item item) {
    ProductRow product = commerce.product(item.productId());
    if (product == null) return line(item.productId(), "商品已不可用", "", "", "", item.quantity(), null, null);
    return line(item.productId(), product.getName(), product.getSku(), product.getDescription(),
        product.getImageUrl(), item.quantity(), product.getPrice(), product.getPrice().multiply(BigDecimal.valueOf(item.quantity())));
  }

  private Map<String, Object> line(long productId, String name, String sku, String description,
      String imageUrl, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    Map<String, Object> line = new LinkedHashMap<>();
    line.put("productId", productId); line.put("name", name); line.put("sku", sku);
    line.put("description", description); line.put("imageUrl", imageUrl); line.put("quantity", quantity);
    line.put("unitPrice", unitPrice); line.put("lineTotal", lineTotal);
    return line;
  }

  private List<Item> readItems(CheckoutTicket ticket) {
    try { return json.readValue(ticket.getPayloadJson(), new TypeReference<List<Item>>() {}); }
    catch (Exception invalid) { throw new BusinessException("提交的商品记录不可用，请取消后重新提交"); }
  }

  private CheckoutTicket requireTicket(CheckoutTicket ticket) {
    if (ticket == null) throw new BusinessException(404, "票据不存在");
    return ticket;
  }

  private void verifyCashier(CheckoutTicket ticket, CurrentUser actor) {
    if (!Objects.equals(ticket.getCashierId(), actor.id())) throw new BusinessException(403, "请由分配的收银员确认该票据");
  }

  private void verifyOwner(CheckoutTicket ticket, CurrentUser actor) {
    if (!Objects.equals(ticket.getMemberId(), actor.id())) throw new BusinessException(403, "无权操作该票据");
  }

  private void requireMember(CurrentUser actor) {
    if (actor.role() != Role.MEMBER) throw new BusinessException(403, "仅会员可以排队结账");
  }
}
