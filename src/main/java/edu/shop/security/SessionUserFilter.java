package edu.shop.security;

import edu.shop.user.ShopUser;
import edu.shop.user.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 每个 API 请求重新确认用户未被禁用；禁用后旧会话也立即失效。 */
@Component
public class SessionUserFilter extends OncePerRequestFilter {
  private final UserMapper users;

  /** 构造方法注入数据库访问对象。 */
  public SessionUserFilter(UserMapper users) {
    this.users = users;
  }

  /** 不处理静态页面，避免页面资源反复查询数据库。 */
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getServletPath();
    // MockMvc 和部分 Servlet 映射下 servletPath 为空，此时仍需验证请求身份。
    if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
    return !(path.startsWith("/api/")
        || path.equals("/api-docs.html")
        || path.startsWith("/swagger-ui")
        || path.startsWith("/v3/api-docs"));
  }

  /** 将 session 中的身份重新校验后放进 Spring Security 当前线程。 */
  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    var session = req.getSession(false);
    if (session != null && session.getAttribute("SHOP_USER") instanceof CurrentUser saved) {
      ShopUser db = users.findById(saved.id());
      if (db != null && db.isEnabled()) {
        CurrentUser current =
            new CurrentUser(db.getId(), db.getUsername(), db.getDisplayName(), db.getRole());
        var authentication =
            new UsernamePasswordAuthenticationToken(
                current, null, List.of(new SimpleGrantedAuthority("ROLE_" + db.getRole().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } else {
        session.invalidate();
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(req, res);
  }
}
