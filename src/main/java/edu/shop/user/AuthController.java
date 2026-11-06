package edu.shop.user;

import edu.shop.common.*;
import edu.shop.discount.MemberLevel;
import edu.shop.security.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 登录与会员注册。数据库管理员 root 和这里的数码店账号属于不同系统。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final UserMapper users;
  private final PasswordEncoder passwords;
  private final AuthContext auth;

  /** 将数据库访问、密码算法、当前身份三个对象通过构造方法传入。 */
  public AuthController(UserMapper users, PasswordEncoder passwords, AuthContext auth) {
    this.users = users;
    this.passwords = passwords;
    this.auth = auth;
  }

  public record LoginRequest(
      @NotBlank @Size(max = 40) String username, @NotBlank @Size(max = 72) String password) {}

  public record RegisterRequest(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,30}", message = "用户名使用3至30位字母数字下划线")
          String username,
      @NotBlank @Size(min = 8, max = 60) String password,
      @NotBlank @Size(max = 60) String displayName) {}

  /** 获取防跨站请求token；GET此接口会创建会话。 */
  @GetMapping("/csrf")
  public ApiResponse<?> csrf(CsrfToken token) {
    return ApiResponse.ok(Map.of("token", token.getToken(), "headerName", token.getHeaderName()));
  }

  /** 校验密码后更换会话编号，防会话固定攻击；原始密码不会写入数据库。 */
  @PostMapping("/login")
  public ApiResponse<?> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
    validatePasswordBytes(body.password());
    ShopUser user = users.findByUsername(body.username().trim());
    if (user == null
        || !passwords.matches(body.password(), user.getPasswordHash())
        || !user.isEnabled()) throw new BusinessException(401, "用户名或密码错误，或账号已被停用");
    HttpSession session = request.getSession(true);
    request.changeSessionId();
    session.setAttribute(
        "SHOP_USER",
        new CurrentUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole()));
    return ApiResponse.ok(profile(user));
  }

  /** 新注册者只能是会员，不能通过JSON指定老板身份或初始积分。 */
  @PostMapping("/register")
  @Transactional
  public ApiResponse<?> register(@Valid @RequestBody RegisterRequest body) {
    validatePasswordBytes(body.password());
    if (body.username() == null) throw new BusinessException("用户名不能为空");
    if (body.username().toLowerCase(java.util.Locale.ROOT).startsWith("demo_"))
      throw new BusinessException("demo_ 是系统演示账号保留前缀，请换一个用户名");
    if (users.findByUsername(body.username()) != null) throw new BusinessException(409, "用户名已存在");
    ShopUser user = new ShopUser();
    user.setUsername(body.username());
    user.setDisplayName(body.displayName().trim());
    user.setPasswordHash(passwords.encode(body.password()));
    user.setRole(Role.MEMBER);
    user.setPoints(0);
    user.setTotalSpent(BigDecimal.ZERO);
    user.setEnabled(true);
    users.insert(user);
    return ApiResponse.ok(Map.of("id", user.getId(), "username", user.getUsername()));
  }

  /** 读取最新积分、累计消费和会员等级。 */
  @GetMapping("/me")
  public ApiResponse<?> me() {
    return ApiResponse.ok(profile(users.findById(auth.require().id())));
  }

  /** 注销当前会话，浏览器不再能使用旧会话调用API。 */
  @PostMapping("/logout")
  public ApiResponse<?> logout(HttpServletRequest request) {
    var session = request.getSession(false);
    if (session != null) session.invalidate();
    SecurityContextHolder.clearContext();
    return ApiResponse.ok();
  }

  /** 公开响应使用白名单字段，从源头避免泄露哈希。 */
  private Map<String, Object> profile(ShopUser user) {
    BigDecimal spent = user.getTotalSpent();
    // 页面资料和结账复用同一个枚举，避免会员门槛在两处修改后不一致。
    MemberLevel level = MemberLevel.fromTotalSpent(spent);
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", user.getId());
    value.put("username", user.getUsername());
    value.put("displayName", user.getDisplayName());
    value.put("role", user.getRole());
    value.put("points", user.getPoints());
    value.put("totalSpent", spent);
    value.put("level", level.getDisplayName());
    value.put("discountRate", level.getDiscountRate());
    return value;
  }

  /** BCrypt 最多处理 72 字节；中文等字符在 UTF-8 中会占多个字节。 */
  private void validatePasswordBytes(String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new BusinessException("密码的 UTF-8 长度不能超过 72 字节，请缩短后重试");
    }
  }
}
