package edu.shop.admin;

import edu.shop.catalog.*;
import edu.shop.common.BusinessException;
import edu.shop.security.AuthContext;
import edu.shop.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 老板管理业务：采购、统计、优惠配置和用户启停。 */
@Service
public class AdminService {
  private final AdminMapper mapper;
  private final CatalogMapper products;
  private final UserMapper users;
  private final AuditMapper audit;
  private final InventoryMapper inventory;
  private final AuthContext auth;
  private final CashierService cashiers;

  /** 构造方法注入各模块协作对象。 */
  public AdminService(
      AdminMapper mapper,
      CatalogMapper products,
      UserMapper users,
      AuditMapper audit,
      InventoryMapper inventory,
      AuthContext auth,
      CashierService cashiers) {
    this.mapper = mapper;
    this.products = products;
    this.users = users;
    this.audit = audit;
    this.inventory = inventory;
    this.auth = auth;
    this.cashiers = cashiers;
  }

  public record SupplierRequest(
      @NotBlank @Size(max = 100) String name,
      @Size(max = 60) String contact,
      @Size(max = 40) String phone) {}

  public record PurchaseLine(
      @NotNull Long productId,
      @Min(1) @Max(100000) int quantity,
      @NotNull @DecimalMin("0") @DecimalMax("999999.99") @Digits(integer = 6, fraction = 2)
          BigDecimal unitCost) {}

  public record PurchaseRequest(
      @NotNull Long supplierId, @NotEmpty @Size(max = 100) List<@Valid PurchaseLine> items) {}

  public record SettingsRequest(
      @NotNull @DecimalMin("0.10") @DecimalMax("1.00") @Digits(integer = 1, fraction = 2)
          BigDecimal productDiscountRate,
      @NotNull @DecimalMin("0.01") @DecimalMax("999999") @Digits(integer = 6, fraction = 2)
          BigDecimal fullReductionThreshold,
      @NotNull @DecimalMin("0") @DecimalMax("999999") @Digits(integer = 6, fraction = 2)
          BigDecimal fullReductionAmount,
      @Min(1) @Max(4) int counterCount) {}

  /** 只有老板能访问供应商信息。 */
  public List<Map<String, Object>> suppliers() {
    auth.requireRole(Role.ADMIN);
    return mapper.suppliers();
  }

  /** 新增或修改供应商。 */
  @Transactional
  public void supplier(Long id, SupplierRequest body) {
    var user = auth.requireRole(Role.ADMIN);
    String contact = body.contact() == null ? "" : body.contact().trim();
    String phone = body.phone() == null ? "" : body.phone().trim();
    if (id == null) mapper.addSupplier(body.name().trim(), contact, phone);
    else if (mapper.updateSupplier(id, body.name().trim(), contact, phone) == 0)
      throw new BusinessException(404, "供应商不存在");
    audit.add(user.id(), "维护供应商", body.name().trim());
  }

  /** 删除无采购引用的供应商。 */
  @Transactional
  public void deleteSupplier(long id) {
    var user = auth.requireRole(Role.ADMIN);
    if (mapper.deleteSupplier(id) == 0) throw new BusinessException(404, "供应商不存在");
    audit.add(user.id(), "删除供应商", "供应商编号 " + id);
  }

  /** 采购必须先按商品编号加锁，避免与结账/退款发生反向锁顺序。 */
  @Transactional
  public Map<String, Object> purchase(PurchaseRequest body) {
    var user = auth.requireRole(Role.ADMIN);
    if (mapper.supplierExists(body.supplierId()) == 0) throw new BusinessException("供应商不存在");
    TreeMap<Long, PurchaseLine> lines = new TreeMap<>();
    for (PurchaseLine line : body.items()) {
      if (lines.put(line.productId(), line) != null) throw new BusinessException("同一商品请合并为一行");
    }
    Map<Long, Product> locked = new HashMap<>();
    BigDecimal total = BigDecimal.ZERO;
    for (PurchaseLine line : lines.values()) {
      Product product = products.lock(line.productId());
      if (product == null) throw new BusinessException("商品不存在");
      if ((long) product.getStock() + line.quantity() > 1000000)
        throw new BusinessException("采购后库存超过100万件限制");
      locked.put(product.getId(), product);
      total = total.add(line.unitCost().multiply(BigDecimal.valueOf(line.quantity())));
    }
    if (total.compareTo(new BigDecimal("9999999999.99")) > 0)
      throw new BusinessException("采购金额超出范围");
    PurchaseOrder order = new PurchaseOrder();
    order.setSupplierId(body.supplierId());
    order.setOperatorId(user.id());
    order.setTotalAmount(total);
    mapper.insertPurchase(order);
    for (PurchaseLine line : lines.values()) {
      mapper.purchaseItem(
          order.getId(),
          line.productId(),
          line.quantity(),
          line.unitCost(),
          line.unitCost().multiply(BigDecimal.valueOf(line.quantity())));
      products.addStock(line.productId(), line.quantity());
      inventory.purchase(
          line.productId(),
          line.quantity(),
          locked.get(line.productId()).getStock() + line.quantity(),
          "PO" + order.getId(),
          user.id());
    }
    audit.add(user.id(), "采购入库", "采购单 PO" + order.getId() + "，金额 " + total);
    return Map.of("id", order.getId(), "totalAmount", total);
  }

  /** 读取采购主表并装配明细。 */
  public List<Map<String, Object>> purchases() {
    auth.requireRole(Role.ADMIN);
    return mapper.purchases().stream()
        .map(
            row -> {
              Map<String, Object> result = new LinkedHashMap<>(row);
              result.put("items", mapper.purchaseItems(((Number) row.get("id")).longValue()));
              return result;
            })
        .toList();
  }

  /** 所有员工可查库存流水，会员不可查。 */
  public List<Map<String, Object>> inventory() {
    auth.requireRole(Role.ADMIN, Role.SALES);
    return inventory.list();
  }

  /** 给员工收银选择会员，销售员仅看到公开会员信息。 */
  public List<Map<String, Object>> users() {
    var user = auth.requireRole(Role.ADMIN, Role.SALES);
    var list = users.listPublic();
    return user.role() == Role.ADMIN
        ? list
        : list.stream().filter(row -> "MEMBER".equals(row.get("role"))).toList();
  }

  /** 禁用自己会使后台无法操作，因此禁止。 */
  @Transactional
  public void enabled(long id, boolean enabled) {
    var user = auth.requireRole(Role.ADMIN);
    if (id == user.id()) throw new BusinessException("不能停用当前登录的老板账号");
    ShopUser target = users.findById(id);
    if (target != null && target.getRole() == Role.SALES) {
      cashiers.enabled(id, enabled);
      return;
    }
    if (users.setEnabled(id, enabled) == 0) throw new BusinessException(404, "用户不存在");
    // 上面的 UPDATE 已持有用户行锁；提交也会锁同一会员，检查与停用不会被并发提交穿过。
    if (!enabled && users.activeMemberTickets(id) > 0)
      throw new BusinessException("该会员还有待确认商品，请先由收银员办理或让会员取消提交，再停用账号");
    audit.add(user.id(), enabled ? "启用用户" : "停用用户", "用户编号 " + id);
  }

  /** 登录用户可读取优惠规则，只有老板能够修改。 */
  public Map<String, Object> settings() {
    auth.require();
    Map<String, String> values = new HashMap<>();
    for (var row : mapper.settings())
      values.put((String) row.get("settingKey"), (String) row.get("settingValue"));
    return Map.of(
        "productDiscountRate",
        new BigDecimal(values.get("PRODUCT_DISCOUNT_RATE")),
        "fullReductionThreshold",
        new BigDecimal(values.get("FULL_REDUCTION_THRESHOLD")),
        "fullReductionAmount",
        new BigDecimal(values.get("FULL_REDUCTION_AMOUNT")),
        "counterCount",
        Integer.parseInt(values.get("COUNTER_COUNT")));
  }

  /** 优惠规则作为一个事务更新，避免页面看到半套规则。 */
  @Transactional
  public void settings(SettingsRequest body) {
    var user = auth.requireRole(Role.ADMIN);
    if (body.fullReductionAmount().compareTo(body.fullReductionThreshold()) > 0)
      throw new BusinessException("满减金额不能大于门槛");
    mapper.updateSetting("PRODUCT_DISCOUNT_RATE", body.productDiscountRate().toPlainString());
    mapper.updateSetting("FULL_REDUCTION_THRESHOLD", body.fullReductionThreshold().toPlainString());
    mapper.updateSetting("FULL_REDUCTION_AMOUNT", body.fullReductionAmount().toPlainString());
    mapper.updateSetting("COUNTER_COUNT", Integer.toString(body.counterCount()));
    audit.add(
        user.id(),
        "修改优惠与柜台",
        "普通折扣 "
            + body.productDiscountRate()
            + "，满 "
            + body.fullReductionThreshold()
            + " 减 "
            + body.fullReductionAmount()
            + "，柜台 "
            + body.counterCount());
  }

  /** 销售统计按已付款未退款订单实时计算。 */
  public Map<String, Object> stats() {
    auth.requireRole(Role.ADMIN);
    Map<String, Object> result = new LinkedHashMap<>(mapper.totals());
    result.put("memberCount", mapper.members());
    result.put("productCount", mapper.products());
    result.put(
        "lowStock",
        products.list(null, null, false).stream()
            .filter(
                p -> !p.getSku().startsWith("DEMO-") && p.getStock() <= p.getLowStockThreshold())
            .toList());
    result.put("dailySales", mapper.dailySales());
    result.put("hotProducts", mapper.hotProducts());
    result.put("cashierPerformance", mapper.cashierPerformance());
    return result;
  }

  /** 老板读取日志。 */
  public List<Map<String, Object>> audit() {
    auth.requireRole(Role.ADMIN);
    return audit.list();
  }

  /** UTF-8 BOM 保证Excel中文；引号和潜在公式都在单元格编码时处理。 */
  public byte[] exportOrders() {
    auth.requireRole(Role.ADMIN);
    StringBuilder csv = new StringBuilder("\uFEFF订单号,状态,会员,收银员,原价,实付,创建时间\r\n");
    for (var row : mapper.exportOrders()) {
      StringJoiner line = new StringJoiner(",");
      for (String key :
          List.of(
              "orderNo",
              "status",
              "memberName",
              "cashierName",
              "originalAmount",
              "payableAmount",
              "createdAt")) line.add(csvCell(row.get(key)));
      csv.append(line).append("\r\n");
    }
    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  /** CSV引号翻倍；公式前缀加单引号，避免表格软件把用户文本执行为公式。 */
  public static String csvCell(Object value) {
    String text = value == null ? "" : value.toString();
    if (!text.isEmpty() && "=+-@\t\r\n".indexOf(text.charAt(0)) >= 0) text = "'" + text;
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }
}
