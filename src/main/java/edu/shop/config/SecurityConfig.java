package edu.shop.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.shop.common.ApiResponse;
import edu.shop.security.SessionUserFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/** 安全配置：会话认证、CSRF、防止匿名调用业务API；业务角色在服务端再次检查。 */
@Configuration
public class SecurityConfig {
  /** BCrypt 自动使用随机盐，同一个密码每次计算出的哈希也不同。 */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
  }

  /** 过滤器只放进安全链一次，不再交由Servlet容器重复执行。 */
  @Bean
  public FilterRegistrationBean<SessionUserFilter> disableDuplicateRegistration(
      SessionUserFilter filter) {
    FilterRegistrationBean<SessionUserFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  /** 定义哪些路径可以匿名访问；CSRF 默认启用，前端必须携带token。 */
  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, SessionUserFilter filter, ObjectMapper json) throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/",
                        "/index.html",
                        "/app.js",
                        "/styles.css",
                        "/member-ui.js",
                        "/member-ui.css",
                        "/staff-ui.js",
                        "/staff-ui.css",
                        "/polish.css",
                        "/images/**",
                        "/favicon.svg",
                        "/favicon.ico",
                        "/error",
                        "/api/auth/csrf",
                        "/api/auth/login",
                        "/api/auth/register")
                    .permitAll()
                    .requestMatchers("/api-docs.html", "/swagger-ui/**", "/v3/api-docs/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .addFilterBefore(filter, AnonymousAuthenticationFilter.class)
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (req, res, ex) -> {
                          res.setStatus(401);
                          res.setContentType("application/json;charset=UTF-8");
                          json.writeValue(res.getWriter(), ApiResponse.fail("请先登录"));
                        })
                    .accessDeniedHandler(
                        (req, res, ex) -> {
                          res.setStatus(403);
                          res.setContentType("application/json;charset=UTF-8");
                          json.writeValue(res.getWriter(), ApiResponse.fail("没有权限或页面令牌已过期，请刷新后重试"));
                        }))
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable());
    return http.build();
  }
}
