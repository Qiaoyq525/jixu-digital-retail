package edu.shop.config;

import edu.shop.catalog.*;
import edu.shop.user.*;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** 首次运行空库时生成可操作的演示资料；以后重启不会覆盖你的业务数据。 */
@Component
public class DemoDataInitializer implements ApplicationRunner {
  private final UserMapper users;
  private final CatalogMapper products;
  private final PasswordEncoder passwords;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final boolean enabled;

  /** 注入数据对象和是否开启演示种子的配置。 */
  public DemoDataInitializer(
      UserMapper users,
      CatalogMapper products,
      PasswordEncoder passwords,
      JdbcTemplate jdbc,
      TransactionTemplate transactions,
      @Value("${shop.demo.seed:true}") boolean enabled) {
    this.users = users;
    this.products = products;
    this.passwords = passwords;
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.enabled = enabled;
  }

  /** ApplicationRunner 在数据库迁移完成后执行，整批种子在事务中插入。 */
  @Override
  public void run(ApplicationArguments args) {
    if (!enabled || users.count() != 0) return;
    transactions.executeWithoutResult(
        tx -> {
          addUser("admin", "Admin@12345", "乔胤淇", Role.ADMIN, "0");
          addUser("cashier1", "Sales@12345", "小周", Role.SALES, "0");
          addUser("cashier2", "Sales@12345", "小陈", Role.SALES, "0");
          addUser("member1", "Member@12345", "小雨", Role.MEMBER, "0");
          addUser("member2", "Member@12345", "阿宁", Role.MEMBER, "0");
          for (String name : new String[] {"手机通讯", "电脑整机", "智能穿戴", "显卡硬件", "键鼠外设"})
            products.addCategory(name);
          var categories = products.categories();
          addProduct(
              ((Number) categories.get(0).get("id")).longValue(),
              "PHONE-001",
              "iPhone 16",
              "128GB · A18芯片 · 6.1英寸 OLED · 黑色",
              "5499.00",
              48,
              "📱");
          addProduct(
              ((Number) categories.get(0).get("id")).longValue(),
              "PHONE-002",
              "HUAWEI nova 13",
              "12GB+256GB · 6.7英寸 OLED · 100W快充 · 羽砂紫",
              "2699.00",
              24,
              "📱");
          addProduct(
              ((Number) categories.get(0).get("id")).longValue(),
              "PHONE-003",
              "Xiaomi 15",
              "12GB+256GB · 骁龙8至尊版 · 6.36英寸 · 浅草绿",
              "4299.00",
              36,
              "📱");
          addProduct(
              ((Number) categories.get(0).get("id")).longValue(),
              "PHONE-004",
              "vivo X200",
              "12GB+256GB · 天玑9400 · 6.67英寸 · 极光绿",
              "3999.00",
              30,
              "📱");
          addProduct(
              ((Number) categories.get(1).get("id")).longValue(),
              "PC-001",
              "联想 ThinkBook 14",
              "锐龙 7 7730U · 16GB+512GB SSD · 14英寸 · 灰色",
              "4599.00",
              18,
              "💻");
          addProduct(
              ((Number) categories.get(1).get("id")).longValue(),
              "PC-002",
              "MacBook Air 13英寸",
              "M4芯片 · 16GB+256GB SSD · 13.6英寸 · 午夜色",
              "7499.00",
              20,
              "💻");
          addProduct(
              ((Number) categories.get(1).get("id")).longValue(),
              "PC-003",
              "华硕 天选5 Pro",
              "i7-13650HX · RTX 4060 · 16GB+1TB SSD · 16英寸 · 灰色",
              "6999.00",
              4,
              "💻");
          addProduct(
              ((Number) categories.get(2).get("id")).longValue(),
              "WATCH-001",
              "华为 WATCH GT 5",
              "46mm · 心率与睡眠监测 · 双频定位 · 黑色氟橡胶表带",
              "1488.00",
              15,
              "⌚");
          addProduct(
              ((Number) categories.get(2).get("id")).longValue(),
              "WATCH-002",
              "Apple Watch Series 10",
              "42mm · GPS版 · 亮黑色铝金属表壳 · 黑色运动型表带",
              "2599.00",
              8,
              "⌚");
          addProduct(
              ((Number) categories.get(3).get("id")).longValue(),
              "GPU-001",
              "微星 RTX 4060 VENTUS 2X",
              "8GB GDDR6 · 双风扇 · PCIe 4.0 · 黑色 OC 版",
              "2199.00",
              25,
              "🖥️");
          addProduct(
              ((Number) categories.get(3).get("id")).longValue(),
              "GPU-002",
              "技嘉 RTX 4070 SUPER GAMING OC",
              "12GB GDDR6X · 三风扇 · RGB 灯环 · GAMING OC",
              "4599.00",
              60,
              "🖥️");
          addProduct(
              ((Number) categories.get(3).get("id")).longValue(),
              "GPU-003",
              "蓝宝石 RX 7800 XT NITRO+",
              "16GB GDDR6 · 三风扇 · ARGB 灯带 · NITRO+",
              "3699.00",
              3,
              "🖥️");
          addProduct(
              ((Number) categories.get(4).get("id")).longValue(),
              "MOUSE-001",
              "罗技 G304 无线鼠标",
              "LIGHTSPEED 无线 · HERO 12K传感器 · 6个可编程按键 · 黑色",
              "199.00",
              20,
              "🖱️");
          addProduct(
              ((Number) categories.get(4).get("id")).longValue(),
              "KEY-001",
              "罗技 K380s 蓝牙键盘",
              "Pebble Keys 2 · 多设备蓝牙 · 圆形低噪按键 · 石墨灰",
              "249.00",
              16,
              "⌨️");
          jdbc.update(
              "INSERT INTO supplier(name,contact,phone) VALUES(?,?,?)",
              "华讯数码供应链",
              "王经理",
              "13800000001");
          jdbc.update(
              "INSERT INTO supplier(name,contact,phone) VALUES(?,?,?)",
              "星联电脑配件商贸",
              "李女士",
              "13800000002");
          jdbc.update(
              "INSERT INTO audit_log(operator_id,action,detail) VALUES(?,?,?)",
              users.findByUsername("admin").getId(),
              "初始化演示资料",
              "首次空库创建极序数码账号、分类和电子商品；不预造销售额");
        });
  }

  /** 演示密码也只保存BCrypt哈希；0消费对应普通会员。 */
  private void addUser(
      String username, String password, String displayName, Role role, String spent) {
    ShopUser user = new ShopUser();
    user.setUsername(username);
    user.setPasswordHash(passwords.encode(password));
    user.setDisplayName(displayName);
    user.setRole(role);
    user.setPoints(0);
    user.setTotalSpent(new BigDecimal(spent));
    user.setEnabled(true);
    users.insert(user);
  }

  /** 种子商品仍然写入MySQL；之后页面可编辑，不是写死在前端的假数据。 */
  private void addProduct(
      long categoryId,
      String sku,
      String name,
      String description,
      String price,
      int stock,
      String emoji) {
    Product p = new Product();
    p.setCategoryId(categoryId);
    p.setSku(sku);
    p.setName(name);
    p.setDescription(description);
    p.setPrice(new BigDecimal(price));
    p.setStock(stock);
    p.setLowStockThreshold(5);
    p.setOnSale(true);
    p.setEmoji(emoji);
    p.setImageUrl("/images/products/" + sku + ".webp");
    products.insert(p);
    jdbc.update(
        "INSERT INTO"
            + " inventory_log(product_id,change_qty,stock_after,type,reference_no,operator_id,remark)"
            + " VALUES(?,?,?,'INITIAL','SEED',?,'演示商品初始库存')",
        p.getId(),
        stock,
        stock,
        users.findByUsername("admin").getId());
  }
}
