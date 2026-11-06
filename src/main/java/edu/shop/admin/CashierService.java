package edu.shop.admin;

import edu.shop.common.BusinessException;
import edu.shop.queue.CheckoutCounter;
import edu.shop.queue.QueueMapper;
import edu.shop.queue.QueueService;
import edu.shop.security.AuthContext;
import edu.shop.user.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 店长开设员工账号并安排柜台；名额包含停用账号，避免不断新增历史员工。 */
@Service
public class CashierService {
  public static final int LIMIT = 4;
  private final CashierMapper mapper;
  private final UserMapper users;
  private final QueueMapper counters;
  private final QueueService queue;
  private final PasswordEncoder passwords;
  private final AuthContext auth;
  private final AuditMapper audit;

  public CashierService(CashierMapper mapper, UserMapper users, QueueMapper counters,
      QueueService queue, PasswordEncoder passwords, AuthContext auth, AuditMapper audit) {
    this.mapper = mapper;
    this.users = users;
    this.counters = counters;
    this.queue = queue;
    this.passwords = passwords;
    this.auth = auth;
    this.audit = audit;
  }

  public record CashierView(long id, String username, String displayName,
      boolean enabled, Integer counterNo) {}

  public record CreateRequest(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,30}", message = "用户名使用3至30位字母数字下划线") String username,
      @NotBlank @Size(min = 8, max = 60) String password,
      @NotBlank @Size(max = 60) String displayName,
      @Min(1) @Max(4) int counterNo) {}

  /** 只返回账号资料与柜台状态，不返回密码或其他会员的购物清单。 */
  public Map<String, Object> list() {
    var actor = auth.requireRole(Role.ADMIN);
    List<CashierView> rows = mapper.list();
    return Map.of("cashiers", rows, "counters", queue.snapshot(actor).get("counters"),
        "limit", LIMIT, "total", rows.size());
  }

  /** 四个固定柜台也是名额锁；必须在检查名额之前取得锁。 */
  @Transactional
  public CashierView create(CreateRequest body) {
    var actor = auth.requireRole(Role.ADMIN);
    if (body.password().getBytes(StandardCharsets.UTF_8).length > 72)
      throw new BusinessException("密码的 UTF-8 长度不能超过 72 字节，请缩短后重试");
    if (body.username().toLowerCase(Locale.ROOT).startsWith("demo_"))
      throw new BusinessException("demo_ 是系统保留前缀，请换一个用户名");
    String hash = passwords.encode(body.password());
    List<CheckoutCounter> locked = counters.lockCounters();
    if (mapper.lockCashierIds().size() >= LIMIT)
      throw new BusinessException(409, "收银员账号最多 4 个，停用账号也占名额，请使用已有账号");
    CheckoutCounter target = requireCounter(locked, body.counterNo());
    requireFree(target, null);
    if (users.findByUsername(body.username()) != null)
      throw new BusinessException(409, "用户名已存在");
    ShopUser user = new ShopUser();
    user.setUsername(body.username());
    user.setDisplayName(body.displayName().trim());
    user.setPasswordHash(hash);
    user.setRole(Role.SALES);
    user.setPoints(0);
    user.setTotalSpent(BigDecimal.ZERO);
    user.setEnabled(true);
    try {
      users.insert(user);
    } catch (DuplicateKeyException duplicate) {
      throw new BusinessException(409, "用户名已存在");
    }
    requireIdle(user.getId(), null, body.counterNo());
    counters.assignCounter(body.counterNo(), user.getId());
    mapper.openThrough(body.counterNo());
    audit.add(actor.id(), "新增收银员", user.getUsername() + "，安排至 " + body.counterNo() + " 号收银台");
    return view(user, body.counterNo());
  }

  /** 有待办时拒绝换台，让会员和收银员始终对应同一张等待票据。 */
  @Transactional
  public CashierView assign(long id, int counterNo) {
    var actor = auth.requireRole(Role.ADMIN);
    List<CheckoutCounter> locked = counters.lockCounters();
    ShopUser user = requireCashier(id);
    CheckoutCounter target = requireCounter(locked, counterNo);
    Integer source = assignedCounter(locked, id);
    requireFree(target, id);
    if (!Objects.equals(source, counterNo)) {
      requireIdle(id, source, counterNo);
      mapper.clearCounter(id);
      counters.assignCounter(counterNo, id);
    }
    mapper.openThrough(counterNo);
    audit.add(actor.id(), "安排收银台", user.getUsername() + " → " + counterNo + " 号收银台");
    return view(user, counterNo);
  }

  /** 停用前检查已分配待办；原有通用用户接口也必须经过这里。 */
  @Transactional
  public void enabled(long id, boolean enabled) {
    var actor = auth.requireRole(Role.ADMIN);
    List<CheckoutCounter> locked = counters.lockCounters();
    ShopUser user = requireCashier(id);
    Integer counterNo = assignedCounter(locked, id);
    if (!enabled) requireIdle(id, counterNo, counterNo);
    users.setEnabled(id, enabled);
    if (enabled && counterNo != null) mapper.openThrough(counterNo);
    audit.add(actor.id(), enabled ? "启用收银员" : "停用收银员", user.getUsername());
  }

  private ShopUser requireCashier(long id) {
    ShopUser user = users.findById(id);
    if (user == null || user.getRole() != Role.SALES)
      throw new BusinessException(404, "收银员不存在");
    return user;
  }

  private CheckoutCounter requireCounter(List<CheckoutCounter> locked, int number) {
    return locked.stream().filter(c -> c.getCounterNo() == number).findFirst()
        .orElseThrow(() -> new BusinessException("请选择 1 至 4 号收银台"));
  }

  private Integer assignedCounter(List<CheckoutCounter> locked, long id) {
    return locked.stream().filter(c -> Objects.equals(c.getCashierId(), id))
        .map(CheckoutCounter::getCounterNo).findFirst().orElse(null);
  }

  private void requireFree(CheckoutCounter target, Long id) {
    if (target.getCashierId() != null && !Objects.equals(target.getCashierId(), id))
      throw new BusinessException(409, "该收银台已有收银员，请选择空闲收银台");
  }

  private void requireIdle(long id, Integer source, Integer target) {
    if (!mapper.lockActiveTickets(id, source, target).isEmpty())
      throw new BusinessException(409, "该收银员或收银台还有待确认商品，请先完成收银或由会员取消，再调整人员");
  }

  private CashierView view(ShopUser user, Integer counterNo) {
    return new CashierView(user.getId(), user.getUsername(), user.getDisplayName(), user.isEnabled(), counterNo);
  }
}
