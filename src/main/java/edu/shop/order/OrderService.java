package edu.shop.order;

import static edu.shop.order.OrderRequests.*;

import edu.shop.common.BusinessException;
import edu.shop.discount.DiscountCalculator;
import edu.shop.discount.MemberLevel;
import edu.shop.security.CurrentUser;
import edu.shop.user.Role;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 订单业务服务。支付/退款用 TransactionTemplate 明确事务边界：库存、订单和积分一起成功或一起回滚。 数据库行锁负责跨线程库存安全；JVM 内的锁不能代替数据库事务。 */
@Service
public class OrderService {
  private static final BigDecimal MAX_DATABASE_AMOUNT = new BigDecimal("9999999999.99");
  private final CommerceMapper mapper;
  private final TransactionTemplate transactions;
  private final DiscountCalculator calculator = new DiscountCalculator();

  /** 构造方法注入：Spring 把数据库 Mapper 和事务工具传入本对象。 */
  public OrderService(CommerceMapper mapper, TransactionTemplate transactions) {
    this.mapper = mapper;
    this.transactions = transactions;
  }

  /** 试算金额；会员身份始终取登录用户，员工可选已有会员。 */
  public OrderPreview preview(CurrentUser actor, PreviewRequest request) {
    // 两个分支都使用Long；否则long与Long混用会自动拆箱，让散客的null会员编号触发空指针。
    Long memberId = actor.role() == Role.MEMBER ? Long.valueOf(actor.id()) : request.memberId();
    MemberRow member = memberId == null ? null : validMember(mapper.member(memberId));
    rejectDemoMember(member);
    return makePreview(member, normalize(request.items()), false, false);
  }

  /** 员工直接收银入口，会员只能经排队入口结账。 */
  public SaleOrder checkoutFor(CurrentUser actor, CheckoutRequest request) {
    requireStaff(actor);
    validateKey(request.requestKey());
    if (request.requestKey().startsWith("queue:") || request.requestKey().startsWith("demo:")) {
      throw new BusinessException("请求编号不能使用系统内部保留的前缀");
    }
    TreeMap<Long, Integer> quantities = normalize(request.items());
    return executePayment(actor.id(), request.memberId(), quantities, request.requestKey(), false);
  }

  /** 收银员确认排队订单的付款入口：会员、销售员由服务器验证，绝不取客户端传来的身份。 */
  public SaleOrder checkoutQueued(
      long memberId, long cashierId, List<Item> items, String requestKey) {
    MemberRow cashier = mapper.member(cashierId);
    if (cashier == null
        || !Boolean.TRUE.equals(cashier.getEnabled())
        || !"SALES".equals(cashier.getRole())) {
      throw new BusinessException("收银员当前不可用，请重新排队");
    }
    validateKey(requestKey);
    return executePayment(cashierId, memberId, normalize(items), requestKey, true);
  }

  /** 开启支付事务；在队列事务中调用时会加入外层事务，订单和票据一并提交。 */
  private SaleOrder executePayment(
      Long cashierId,
      Long memberId,
      TreeMap<Long, Integer> quantities,
      String requestKey,
      boolean consumeCart) {
    try {
      return transactions.execute(
          status -> pay(cashierId, memberId, quantities, requestKey, consumeCart));
    } catch (DuplicateKeyException duplicate) {
      // 普通收银时，支付事务已经回滚，随后才能用新会话读取并发请求生成的订单。
      SaleOrder existing = mapper.byRequestKey(requestKey);
      if (existing == null) throw duplicate;
      verifyPaymentRequest(existing, cashierId, memberId, quantities);
      return loadItems(existing);
    }
  }

  /** 事务内执行：先锁会员，再按商品编号升序锁商品，减少死锁并保证不会超卖。 */
  private SaleOrder pay(
      Long cashierId,
      Long memberId,
      TreeMap<Long, Integer> quantities,
      String requestKey,
      boolean consumeCart) {
    MemberRow member = memberId == null ? null : validMember(mapper.lockMember(memberId));
    SaleOrder existing = mapper.byRequestKey(requestKey);
    if (existing != null) {
      verifyPaymentRequest(existing, cashierId, memberId, quantities);
      return loadItems(existing);
    }
    boolean demo = requestKey.startsWith("demo:");
    if (!demo) rejectDemoMember(member);
    OrderPreview preview = makePreview(member, quantities, true, demo);
    if (member != null) validateMemberBalance(member, preview);
    SaleOrder order = new SaleOrder();
    order.setOrderNo(
        "SD"
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase(Locale.ROOT));
    order.setMemberId(memberId);
    order.setCashierId(cashierId);
    order.setStatus("PAID");
    order.setOriginalAmount(preview.originalAmount());
    order.setProductDiscount(preview.productDiscount());
    order.setMemberDiscount(preview.memberDiscount());
    order.setFullReduction(preview.fullReduction());
    order.setPayableAmount(preview.payableAmount());
    order.setEarnedPoints(preview.earnedPoints());
    order.setDiscountDescription(preview.discountDescription());
    order.setRequestKey(requestKey);
    mapper.insertOrder(order);
    for (OrderPreview.Line line : preview.items()) {
      if (mapper.decreaseStock(line.productId(), line.quantity()) != 1) {
        throw new BusinessException("商品“" + line.name() + "”库存不足，整笔结账已取消");
      }
      ProductRow updated = mapper.product(line.productId());
      mapper.inventory(
          line.productId(),
          -line.quantity(),
          updated.getStock(),
          "SALE",
          order.getOrderNo(),
          cashierId,
          "模拟支付扣减库存");
      OrderItem item = new OrderItem();
      item.setOrderId(order.getId());
      item.setProductId(line.productId());
      item.setProductName(line.name());
      item.setSku(line.sku());
      item.setDescription(line.description());
      item.setImageUrl(line.imageUrl());
      item.setUnitPrice(line.unitPrice());
      item.setQuantity(line.quantity());
      item.setSubtotal(line.subtotal());
      mapper.insertItem(item);
      if (consumeCart && memberId != null) {
        mapper.removeConsumedCart(memberId, line.productId(), line.quantity());
        mapper.consumeCart(memberId, line.productId(), line.quantity());
      }
    }
    if (member != null) {
      mapper.updateMember(memberId, preview.earnedPoints(), preview.payableAmount());
      mapper.points(
          memberId,
          preview.earnedPoints(),
          member.getPoints() + preview.earnedPoints(),
          order.getId(),
          "EARN");
      if (consumeCart) mapper.clearEmptyCart(memberId);
    }
    mapper.audit(
        cashierId, "CHECKOUT", "模拟结账 " + order.getOrderNo() + "，实付 " + preview.payableAmount());
    return loadItems(mapper.order(order.getId()));
  }

  /** 会员只能申请自己的已支付订单；锁会员再锁订单，和审批保持一致。 */
  public SaleOrder requestRefund(CurrentUser actor, long orderId, String reason) {
    requireMember(actor);
    validateRefundReason(reason);
    SaleOrder snapshot = requireOrder(mapper.order(orderId));
    verifyRefundOwner(actor, snapshot);
    return transactions.execute(status -> {
      validMember(mapper.lockMember(actor.id()));
      SaleOrder order = requireOrder(mapper.lockOrder(orderId));
      verifyRefundOwner(actor, order);
      rejectLegacyRefund(mapper.items(orderId));
      if (!"PAID".equals(order.getStatus())) throw new BusinessException(409, "订单已退款，不能再次申请");
      if (mapper.requestRefund(orderId, reason.trim()) != 1)
        throw new BusinessException(409, "已有待审核退款申请，请等待老板处理");
      mapper.audit(actor.id(), "REFUND_REQUEST", "申请整单退款 " + order.getOrderNo() + "，原因：" + reason.trim());
      return loadItems(mapper.order(orderId));
    });
  }

  /** 审批入口只对老板开放；拒绝不改变订单金额、库存或会员权益。 */
  public SaleOrder reviewRefund(CurrentUser actor, long orderId, boolean approve, String reason) {
    requireRefundAdmin(actor);
    if (reason != null && reason.length() > 200) throw new BusinessException("审核说明不能超过 200 字");
    if (approve) return refundFor(actor, orderId, reason);
    validateRefundReason(reason);
    SaleOrder snapshot = requireOrder(mapper.order(orderId));
    return transactions.execute(status -> {
      if (snapshot.getMemberId() != null) mapper.lockMember(snapshot.getMemberId());
      SaleOrder order = requireOrder(mapper.lockOrder(orderId));
      requirePendingRefund(order);
      if (mapper.reviewRefund(orderId, "REJECTED", reason.trim()) != 1)
        throw new BusinessException(409, "退款申请已处理，请刷新查看");
      mapper.audit(actor.id(), "REFUND_REJECT", "拒绝退款 " + order.getOrderNo() + "，原因：" + reason.trim());
      return loadItems(mapper.order(orderId));
    });
  }

  /** 批准整单退款：会员→商品→订单加锁，审批、库存、积分和订单一起提交。 */
  public SaleOrder refundFor(CurrentUser actor, long orderId, String reason) {
    requireRefundAdmin(actor);
    if (reason != null && reason.length() > 200) throw new BusinessException("审核说明不能超过 200 字");
    String reviewNote = reason == null ? "" : reason.trim();
    SaleOrder snapshot = requireOrder(mapper.order(orderId));
    return transactions.execute(
        status -> {
          MemberRow member =
              snapshot.getMemberId() == null ? null : mapper.lockMember(snapshot.getMemberId());
          List<OrderItem> items = mapper.items(orderId);
          rejectLegacyRefund(items);
          for (OrderItem item : items) {
            ProductRow product = mapper.lockProduct(item.getProductId());
            if (product == null) throw new BusinessException("原订单商品不存在，无法退货");
            if ((long) product.getStock() + item.getQuantity() > 1_000_000) {
              throw new BusinessException("退货后商品库存超过100万件，请先处理库存");
            }
          }
          SaleOrder order = requireOrder(mapper.lockOrder(orderId));
          requirePendingRefund(order);
          if (mapper.markRefunded(orderId) != 1
              || mapper.reviewRefund(orderId, "APPROVED", reviewNote) != 1)
            throw new BusinessException(409, "退款申请已处理，不能重复退款");
          for (OrderItem item : items) {
            mapper.restoreStock(item.getProductId(), item.getQuantity());
            mapper.inventory(
                item.getProductId(),
                item.getQuantity(),
                mapper.product(item.getProductId()).getStock(),
                "REFUND",
                order.getOrderNo(),
                actor.id(),
                order.getRefundReason());
          }
          if (member != null) {
            if (member.getPoints() < order.getEarnedPoints()
                || member.getTotalSpent().compareTo(order.getPayableAmount()) < 0) {
              throw new BusinessException("会员积分或消费余额异常，已取消本次退款");
            }
            mapper.updateMember(
                member.getId(), -order.getEarnedPoints(), order.getPayableAmount().negate());
            mapper.points(
                member.getId(),
                -order.getEarnedPoints(),
                member.getPoints() - order.getEarnedPoints(),
                orderId,
                "REFUND");
          }
          mapper.audit(actor.id(), "REFUND_APPROVE", "批准整单退货 " + order.getOrderNo() + "，申请原因：" + order.getRefundReason() + "，审核说明：" + reviewNote);
          return loadItems(mapper.order(orderId));
        });
  }

  /** 按角色过滤订单：老板全部，销售员仅经手订单，会员仅本人订单。 */
  public List<SaleOrder> listFor(CurrentUser actor) {
    Long memberId = actor.role() == Role.MEMBER ? actor.id() : null;
    Long cashierId = actor.role() == Role.SALES ? actor.id() : null;
    return mapper.orders(memberId, cashierId).stream().map(this::loadItems).toList();
  }

  /** 独立的退款申请列表，避免旧订单被普通列表的数量上限截掉。 */
  public List<SaleOrder> refundRequestsFor(CurrentUser actor) {
    requireRefundAdmin(actor);
    return mapper.refundOrders().stream().map(this::loadItems).toList();
  }

  /** 单张订单同样校验所属人，防止通过修改 URL 编号读取别人的订单。 */
  public SaleOrder getFor(long id, CurrentUser actor) {
    SaleOrder order = requireOrder(mapper.order(id));
    if (actor.role() == Role.MEMBER && !Objects.equals(order.getMemberId(), actor.id())
        || actor.role() == Role.SALES && !Objects.equals(order.getCashierId(), actor.id())) {
      throw new BusinessException(403, "无权查看该订单");
    }
    return loadItems(order);
  }

  /** 获取会员自己的购物车。 */
  public List<CartItem> cartFor(CurrentUser actor) {
    requireMember(actor);
    return mapper.cart(actor.id());
  }

  /** 更新购物车；不提前预占库存，结账时重新检查库存。数量为 0 时删除。 */
  public List<CartItem> updateCart(CurrentUser actor, CartRequest request) {
    requireMember(actor);
    if (request.productId() == null
        || request.productId() <= 0
        || request.quantity() < 0
        || request.quantity() > 999) {
      throw new BusinessException("商品编号或购物车数量不合法");
    }
    return transactions.execute(
        status -> {
          validMember(mapper.lockMember(actor.id()));
          if (request.quantity() == 0) mapper.removeCart(actor.id(), request.productId());
          else {
            ProductRow product = mapper.product(request.productId());
            if (product == null || !Boolean.TRUE.equals(product.getOnSale()))
              throw new BusinessException("商品不存在或已下架");
            if (product.getSku() != null && product.getSku().startsWith("DEMO-"))
              throw new BusinessException("并发实验商品不能加入日常购物车");
            if (request.quantity() > product.getStock()) throw new BusinessException("加入数量超过当前库存");
            if (mapper.cartId(actor.id(), request.productId()) == null)
              mapper.addCart(actor.id(), request.productId(), request.quantity());
            else mapper.updateCart(actor.id(), request.productId(), request.quantity());
          }
          return mapper.cart(actor.id());
        });
  }

  /** 会员读取自己的积分明细。 */
  public List<PointEntry> pointsFor(CurrentUser actor) {
    requireMember(actor);
    return mapper.pointEntries(actor.id());
  }

  /** 将重复商品合并并按编号排序；TreeMap 同时确定统一的数据库加锁顺序。 */
  public TreeMap<Long, Integer> normalize(List<Item> items) {
    if (items == null || items.isEmpty() || items.size() > 100)
      throw new BusinessException("请提交 1～100 种商品");
    TreeMap<Long, Integer> quantities = new TreeMap<>();
    for (Item item : items) {
      if (item == null
          || item.productId() == null
          || item.productId() <= 0
          || item.quantity() < 1
          || item.quantity() > 999) {
        throw new BusinessException("商品编号或购买数量不合法");
      }
      int combined = quantities.getOrDefault(item.productId(), 0) + item.quantity();
      if (combined > 999) throw new BusinessException("单种商品一次最多购买 999 件");
      quantities.put(item.productId(), combined);
    }
    return quantities;
  }

  /** 用实时商品与会员数据计算金额，lock=true 代表支付事务中需要锁商品。 */
  private OrderPreview makePreview(
      MemberRow member, TreeMap<Long, Integer> quantities, boolean lock, boolean demo) {
    List<OrderPreview.Line> lines = new ArrayList<>();
    BigDecimal original = BigDecimal.ZERO;
    for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
      ProductRow product =
          lock ? mapper.lockProduct(entry.getKey()) : mapper.product(entry.getKey());
      if (product == null || !Boolean.TRUE.equals(product.getOnSale()))
        throw new BusinessException("商品不存在或已经下架");
      if (!demo && product.getSku() != null && product.getSku().startsWith("DEMO-")) {
        throw new BusinessException("并发实验商品不能通过日常收银购买");
      }
      if (product.getStock() < entry.getValue())
        throw new BusinessException("商品“" + product.getName() + "”库存不足");
      BigDecimal subtotal =
          product.getPrice().multiply(BigDecimal.valueOf(entry.getValue())).setScale(2);
      original = original.add(subtotal);
      if (original.compareTo(MAX_DATABASE_AMOUNT) > 0) {
        throw new BusinessException("订单金额超出系统支持范围，请分批结账");
      }
      lines.add(
          new OrderPreview.Line(
              product.getId(), product.getName(), product.getPrice(), entry.getValue(), subtotal,
              product.getSku(), product.getDescription(), product.getImageUrl()));
    }
    MemberLevel level =
        member == null ? MemberLevel.NORMAL : MemberLevel.fromTotalSpent(member.getTotalSpent());
    DiscountCalculator.Result result =
        calculator.calculate(
            original,
            decimalSetting("PRODUCT_DISCOUNT_RATE", "1.00"),
            level,
            decimalSetting("FULL_REDUCTION_THRESHOLD", "3000"),
            decimalSetting("FULL_REDUCTION_AMOUNT", "100"),
            member != null);
    return new OrderPreview(
        result.originalAmount(),
        result.productDiscount(),
        result.memberDiscount(),
        result.fullReduction(),
        result.payableAmount(),
        result.earnedPoints(),
        result.discountDescription(),
        List.copyOf(lines));
  }

  /** 校验数据库中的会员身份与启用状态。 */
  private MemberRow validMember(MemberRow member) {
    if (member == null
        || !"MEMBER".equals(member.getRole())
        || !Boolean.TRUE.equals(member.getEnabled())) {
      throw new BusinessException("会员不存在或已停用");
    }
    return member;
  }

  /** 实验身份只在内部并发任务中使用，不能混入日常会员消费。 */
  private void rejectDemoMember(MemberRow member) {
    if (member != null
        && member.getUsername() != null
        && member.getUsername().startsWith("demo_")) {
      throw new BusinessException("并发实验会员不能用于日常收银");
    }
  }

  /** 读取金额设置；缺省值只在数据库未设置时使用。 */
  private BigDecimal decimalSetting(String key, String fallback) {
    String value = mapper.setting(key);
    return new BigDecimal(value == null ? fallback : value);
  }

  /** 幂等重试必须属于同一会员与同一收银员，禁止借别人的 key 获取订单。 */
  private void verifyPaymentOwner(SaleOrder order, Long cashierId, Long memberId) {
    if (!Objects.equals(order.getMemberId(), memberId)
        || !Objects.equals(order.getCashierId(), cashierId)) {
      throw new BusinessException(409, "请求编号已被其他结账使用，请刷新后重试");
    }
  }

  /** 同一个请求编号必须对应同一组商品和数量，不能把另一笔购买误当成成功重试。 */
  private void verifyPaymentRequest(
      SaleOrder order, Long cashierId, Long memberId, TreeMap<Long, Integer> quantities) {
    verifyPaymentOwner(order, cashierId, memberId);
    TreeMap<Long, Integer> originalItems = new TreeMap<>();
    for (OrderItem item : mapper.items(order.getId())) {
      originalItems.merge(item.getProductId(), item.getQuantity(), Integer::sum);
    }
    if (!originalItems.equals(quantities)) {
      throw new BusinessException(409, "该请求编号已经用于其他商品或数量，请重新发起结账");
    }
  }

  /** 提前检查字段容量，避免积分溢出或数据库金额超限后只得到笼统的服务器错误。 */
  private void validateMemberBalance(MemberRow member, OrderPreview preview) {
    long nextPoints = (long) member.getPoints() + preview.earnedPoints();
    BigDecimal nextSpent = member.getTotalSpent().add(preview.payableAmount());
    if (nextPoints > Integer.MAX_VALUE || nextSpent.compareTo(MAX_DATABASE_AMOUNT) > 0) {
      throw new BusinessException("会员积分或累计消费超出系统支持范围，请联系老板处理");
    }
  }

  private void requireRefundAdmin(CurrentUser actor) {
    if (actor.role() != Role.ADMIN) throw new BusinessException(403, "仅老板可以审核退款，收银员只能查看");
  }

  private void verifyRefundOwner(CurrentUser actor, SaleOrder order) {
    if (!Objects.equals(order.getMemberId(), actor.id())) throw new BusinessException(403, "只能申请本人订单的退款");
  }

  private void validateRefundReason(String reason) {
    if (reason == null || reason.isBlank() || reason.length() > 200)
      throw new BusinessException("请输入 1～200 字的原因");
  }

  private void requirePendingRefund(SaleOrder order) {
    if (!"PAID".equals(order.getStatus()) || !"PENDING".equals(order.getRefundStatus()))
      throw new BusinessException(409, "订单没有待审核的申请或已处理，不能重复退款");
  }

  private void rejectLegacyRefund(List<OrderItem> items) {
    if (items.stream().anyMatch(OrderItem::isLegacyDemoItem))
      throw new BusinessException(409, "这是改版前的演示订单，仅保留历史记录，不能退回当前数码商品库存");
  }

  /** 给订单补齐明细和显示名称；不返回内部的幂等键。 */
  private SaleOrder loadItems(SaleOrder order) {
    order.setItems(mapper.items(order.getId()));
    if (order.getMemberId() != null) {
      MemberRow member = mapper.member(order.getMemberId());
      if (member != null) order.setMemberName(member.getDisplayName());
    }
    if (order.getCashierId() != null) {
      MemberRow cashier = mapper.member(order.getCashierId());
      if (cashier != null) order.setCashierName(cashier.getDisplayName());
    }
    order.setRequestKey(null);
    return order;
  }

  /** 将不存在的订单转为明确的 404 业务异常。 */
  private SaleOrder requireOrder(SaleOrder order) {
    if (order == null) throw new BusinessException(404, "订单不存在");
    return order;
  }

  /** 拒绝会员直接调用员工收银接口。 */
  private void requireStaff(CurrentUser actor) {
    if (actor.role() != Role.ADMIN && actor.role() != Role.SALES)
      throw new BusinessException(403, "仅店员可执行此操作");
  }

  /** 购物车和积分属于会员功能。 */
  private void requireMember(CurrentUser actor) {
    if (actor.role() != Role.MEMBER) throw new BusinessException(403, "仅会员可执行此操作");
  }

  /** 幂等请求编号限制长度，并拒绝空白。 */
  private void validateKey(String key) {
    if (key == null || key.isBlank() || key.length() > 100)
      throw new BusinessException("请提供有效的请求编号");
  }
}
