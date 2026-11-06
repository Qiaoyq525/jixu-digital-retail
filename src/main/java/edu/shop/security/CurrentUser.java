package edu.shop.security;

import edu.shop.user.Role;
import java.io.Serializable;

/** 登录后放进会话的公开身份；这里没有密码和密码哈希。 */
public record CurrentUser(long id, String username, String displayName, Role role)
    implements Serializable {}
