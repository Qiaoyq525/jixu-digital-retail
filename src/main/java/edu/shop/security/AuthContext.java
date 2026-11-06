package edu.shop.security;

import edu.shop.common.BusinessException;
import edu.shop.user.Role;
import java.util.Arrays;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 从当前请求的安全上下文取身份。异步任务不能依赖此线程变量，应显式传入身份。 */
@Component
public class AuthContext {
  /** 未登录抛401，已登录返回服务器保存的身份。 */
  public CurrentUser require() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof CurrentUser user))
      throw new BusinessException(401, "请先登录");
    return user;
  }

  /** 要求当前角色在允许列表中，否则抛403。 */
  public CurrentUser requireRole(Role... roles) {
    CurrentUser user = require();
    if (Arrays.stream(roles).noneMatch(role -> role == user.role()))
      throw new BusinessException(403, "没有权限执行此操作");
    return user;
  }
}
