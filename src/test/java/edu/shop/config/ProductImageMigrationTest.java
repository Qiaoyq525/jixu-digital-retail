package edu.shop.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** 验证 V3 同时兼容空库和已营业的旧版数码店，尤其不能改写历史订单。 */
class ProductImageMigrationTest {
  private DriverManagerDataSource source;
  private JdbcTemplate jdbc;

  @BeforeEach
  void schemaBeforeImages() {
    source = new DriverManagerDataSource(
        "jdbc:h2:mem:images_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1", "sa", "");
    jdbc = new JdbcTemplate(source);
    execute("V1__create_shop_tables.sql");
    execute("V2__upgrade_to_digital_store.sql");
  }

  @AfterEach
  void closeDatabase() {
    jdbc.execute("SHUTDOWN");
  }

  @Test
  void freshDatabaseLeavesSeedCreationToJava() {
    execute("V3__product_images_and_order_snapshots.sql");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM category", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shop_user", Integer.class)).isZero();
  }

  @Test
  void upgradeAddsPeripheralsButPreservesStockSaleStatusAndHistoricalSnapshots() {
    jdbc.update("INSERT INTO shop_user(id,username,password_hash,display_name,role)"
        + " VALUES(1,'member1','test-hash','老会员','MEMBER')");
    jdbc.update("INSERT INTO category(id,name) VALUES(1,'手机通讯'),(2,'电脑整机')");
    jdbc.update("INSERT INTO product(id,category_id,sku,name,description,price,stock,on_sale)"
        + " VALUES(1,1,'PHONE-001','iPhone 16','现有规格',5499,7,FALSE)");
    jdbc.update("INSERT INTO product(id,category_id,sku,name,description,price,stock)"
        + " VALUES(2,2,'PC-003','华硕 天选 5','酷睿i7 · RTX 4060 · 16GB+1TB SSD · 15.6英寸游戏本',6666,3)");
    jdbc.update("INSERT INTO shop_order(id,order_no,status,original_amount,payable_amount,request_key)"
        + " VALUES(1,'OLD','PAID',6.5,6.5,'old')");
    jdbc.update("INSERT INTO order_item(order_id,product_id,product_name,unit_price,quantity,subtotal)"
        + " VALUES(1,1,'牧场鲜牛奶',6.5,1,6.5)");
    jdbc.update("INSERT INTO cart_item(member_id,product_id,quantity) VALUES(1,1,2)");

    execute("V3__product_images_and_order_snapshots.sql");

    assertThat(text("SELECT image_url FROM product WHERE id=1"))
        .isEqualTo("/images/products/PHONE-001.webp");
    assertThat(jdbc.queryForObject("SELECT stock FROM product WHERE id=1", Integer.class)).isEqualTo(7);
    assertThat(jdbc.queryForObject("SELECT on_sale FROM product WHERE id=1", Boolean.class)).isFalse();
    assertThat(text("SELECT description FROM product WHERE id=1")).isEqualTo("现有规格");
    assertThat(text("SELECT name FROM product WHERE id=2")).isEqualTo("华硕 天选5 Pro");
    assertThat(jdbc.queryForObject("SELECT price FROM product WHERE id=2", BigDecimal.class))
        .isEqualByComparingTo("6666");
    assertThat(text("SELECT product_name FROM order_item WHERE order_id=1")).isEqualTo("牧场鲜牛奶");
    assertThat(text("SELECT sku FROM order_item WHERE order_id=1")).isEmpty();
    assertThat(text("SELECT description FROM order_item WHERE order_id=1")).isEmpty();
    assertThat(text("SELECT image_url FROM order_item WHERE order_id=1")).isEmpty();
    assertThat(jdbc.queryForObject("SELECT unit_price FROM order_item WHERE order_id=1", BigDecimal.class))
        .isEqualByComparingTo("6.50");
    assertThat(jdbc.queryForObject("SELECT quantity FROM cart_item WHERE product_id=1", Integer.class)).isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT stock FROM product WHERE sku='MOUSE-001'", Integer.class)).isEqualTo(20);
    assertThat(jdbc.queryForObject("SELECT stock FROM product WHERE sku='KEY-001'", Integer.class)).isEqualTo(16);
    assertThat(jdbc.queryForObject("SELECT SUM(change_qty) FROM inventory_log WHERE reference_no='V3-CATALOG'", Integer.class))
        .isEqualTo(36);
  }

  @Test
  void customProductAndExistingPeripheralsAreNotOverwritten() {
    jdbc.update("INSERT INTO category(id,name) VALUES(1,'手机通讯'),(2,'键鼠外设')");
    jdbc.update("INSERT INTO product(category_id,sku,name,description,price,stock)"
        + " VALUES(1,'PHONE-001','我改过的商品','保留规格',88,9),(2,'MOUSE-001','我的鼠标','特别版',77,2)");
    execute("V3__product_images_and_order_snapshots.sql");
    assertThat(text("SELECT image_url FROM product WHERE sku='PHONE-001'")).isEmpty();
    assertThat(text("SELECT name FROM product WHERE sku='MOUSE-001'")).isEqualTo("我的鼠标");
    assertThat(jdbc.queryForObject("SELECT stock FROM product WHERE sku='MOUSE-001'", Integer.class)).isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE sku='MOUSE-001'", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM category WHERE name='键鼠外设'", Integer.class)).isEqualTo(1);
  }

  private String text(String query) {
    return jdbc.queryForObject(query, String.class);
  }

  private void execute(String filename) {
    new ResourceDatabasePopulator(new ClassPathResource("db/migration/" + filename)).execute(source);
  }
}
