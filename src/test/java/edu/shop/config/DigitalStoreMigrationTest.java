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

/** 在隔离库中还原旧种子，再执行真实V2脚本，核对资料升级和历史业务数据保留。 */
class DigitalStoreMigrationTest {
  private DriverManagerDataSource dataSource;
  private JdbcTemplate jdbc;

  @BeforeEach
  void createOldSchema() {
    dataSource =
        new DriverManagerDataSource(
            "jdbc:h2:mem:digital_"
                + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
            "sa",
            "");
    jdbc = new JdbcTemplate(dataSource);
    execute("V1__create_shop_tables.sql");
  }

  @AfterEach
  void closeDatabase() {
    jdbc.execute("SHUTDOWN");
  }

  @Test
  void legacyUpgradePreservesStockOrdersAndTheirSnapshots() {
    jdbc.update(
        "INSERT INTO shop_user(id,username,password_hash,display_name,role)"
            + " VALUES(1,'admin','test-hash','林店长','ADMIN')");
    jdbc.update("INSERT INTO category(id,name) VALUES(1,'饮品茶咖')");
    jdbc.update(
        "INSERT INTO product(id,category_id,sku,name,description,price,stock,on_sale,emoji)"
            + " VALUES(1,1,'DRINK-001','牧场鲜牛奶','旧描述',6.50,47,FALSE,'🥛')");
    jdbc.update(
        "INSERT INTO supplier(id,name,contact,phone) VALUES(1,'晨光食品供应站','王经理','13800000001')");
    jdbc.update(
        "INSERT INTO"
            + " shop_order(id,order_no,cashier_id,status,original_amount,payable_amount,request_key)"
            + " VALUES(1,'OLD-ORDER',1,'PAID',6.50,6.50,'old-key')");
    jdbc.update(
        "INSERT INTO order_item(order_id,product_id,product_name,unit_price,quantity,subtotal)"
            + " VALUES(1,1,'牧场鲜牛奶',6.50,1,6.50)");
    jdbc.update(
        "INSERT INTO inventory_log(product_id,change_qty,stock_after,type,reference_no,operator_id)"
            + " VALUES(1,-1,47,'SALE','OLD-ORDER',1)");
    jdbc.update("INSERT INTO cart_item(member_id,product_id,quantity) VALUES(1,1,2)");
    jdbc.update(
        "INSERT INTO checkout_ticket(member_id,status,payload_json,request_key,order_id)"
            + " VALUES(1,'COMPLETED','[]','old-ticket',1)");
    jdbc.update(
        "INSERT INTO purchase_order(id,supplier_id,operator_id,total_amount) VALUES(1,1,1,10)");
    jdbc.update(
        "INSERT INTO purchase_item(purchase_id,product_id,quantity,unit_cost,subtotal)"
            + " VALUES(1,1,2,5,10)");

    execute("V2__upgrade_to_digital_store.sql");
    assertThat(
            jdbc.queryForObject(
                "SELECT display_name FROM shop_user WHERE username='admin'", String.class))
        .isEqualTo("乔胤淇");
    assertThat(jdbc.queryForObject("SELECT name FROM category WHERE id=1", String.class))
        .isEqualTo("手机通讯");
    assertThat(jdbc.queryForObject("SELECT name FROM supplier WHERE id=1", String.class))
        .isEqualTo("华讯数码供应链");
    assertThat(jdbc.queryForObject("SELECT sku FROM product WHERE id=1", String.class))
        .isEqualTo("PHONE-001");
    assertThat(jdbc.queryForObject("SELECT price FROM product WHERE id=1", BigDecimal.class))
        .isEqualByComparingTo("5499.00");
    assertThat(jdbc.queryForObject("SELECT stock FROM product WHERE id=1", Integer.class))
        .isEqualTo(47);
    assertThat(jdbc.queryForObject("SELECT on_sale FROM product WHERE id=1", Boolean.class))
        .isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT product_name FROM order_item WHERE order_id=1", String.class))
        .isEqualTo("牧场鲜牛奶");
    assertThat(
            jdbc.queryForObject(
                "SELECT unit_price FROM order_item WHERE order_id=1", BigDecimal.class))
        .isEqualByComparingTo("6.50");
    assertThat(
            jdbc.queryForObject(
                "SELECT payable_amount FROM shop_order WHERE id=1", BigDecimal.class))
        .isEqualByComparingTo("6.50");
    assertThat(
            jdbc.queryForObject(
                "SELECT stock_after FROM inventory_log WHERE product_id=1", Integer.class))
        .isEqualTo(47);
    assertThat(
            jdbc.queryForObject("SELECT quantity FROM cart_item WHERE product_id=1", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT order_id FROM checkout_ticket WHERE request_key='old-ticket'", Long.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT product_id FROM purchase_item WHERE purchase_id=1", Long.class))
        .isEqualTo(1);
    assertThat(setting("FULL_REDUCTION_THRESHOLD")).isEqualTo("3000.00");
    assertThat(setting("FULL_REDUCTION_AMOUNT")).isEqualTo("100.00");

    execute("V2__upgrade_to_digital_store.sql");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT stock FROM product WHERE id=1", Integer.class))
        .isEqualTo(47);
    assertThat(setting("FULL_REDUCTION_THRESHOLD")).isEqualTo("3000.00");
  }

  @Test
  void userEditedProductsAndDiscountSettingsAreNotOverwritten() {
    jdbc.update("INSERT INTO category(id,name) VALUES(1,'我改过的分类')");
    jdbc.update(
        "INSERT INTO product(category_id,sku,name,price,stock)"
            + " VALUES(1,'DRINK-002','我改过的商品',88,9)");
    jdbc.update("INSERT INTO supplier(name) VALUES('我改过的供应商')");
    jdbc.update(
        "UPDATE shop_setting SET setting_value='7.00' WHERE setting_key='FULL_REDUCTION_AMOUNT'");
    execute("V2__upgrade_to_digital_store.sql");
    assertThat(jdbc.queryForObject("SELECT name FROM product WHERE sku='DRINK-002'", String.class))
        .isEqualTo("我改过的商品");
    assertThat(
            jdbc.queryForObject(
                "SELECT price FROM product WHERE sku='DRINK-002'", BigDecimal.class))
        .isEqualByComparingTo("88");
    assertThat(jdbc.queryForObject("SELECT name FROM category WHERE id=1", String.class))
        .isEqualTo("我改过的分类");
    assertThat(setting("FULL_REDUCTION_THRESHOLD")).isEqualTo("100.00");
    assertThat(setting("FULL_REDUCTION_AMOUNT")).isEqualTo("7.00");
  }

  @Test
  void freshSchemaCanMigrateBeforeJavaCreatesDigitalSeedData() {
    execute("V2__upgrade_to_digital_store.sql");
    execute("V2__upgrade_to_digital_store.sql");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shop_user", Integer.class)).isZero();
    assertThat(setting("FULL_REDUCTION_THRESHOLD")).isEqualTo("3000.00");
    assertThat(setting("FULL_REDUCTION_AMOUNT")).isEqualTo("100.00");
  }

  private String setting(String key) {
    return jdbc.queryForObject(
        "SELECT setting_value FROM shop_setting WHERE setting_key=?", String.class, key);
  }

  private void execute(String filename) {
    new ResourceDatabasePopulator(new ClassPathResource("db/migration/" + filename))
        .execute(dataSource);
  }
}
