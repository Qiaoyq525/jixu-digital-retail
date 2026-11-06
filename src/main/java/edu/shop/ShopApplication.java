package edu.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/** Web 系统入口。Spring Boot 会创建对象、连接数据库，并启动内置的 Tomcat。 */
// 数码店使用数据库账号和自己的会话登录，不再生成 Spring 的额外默认演示用户。
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ShopApplication {
  /** 在 IDEA 中运行此 main 方法；浏览器访问 http://127.0.0.1:8080。 */
  public static void main(String[] args) {
    SpringApplication.run(ShopApplication.class, args);
  }
}
