package edu.shop;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.shop.order.OrderRequests;
import edu.shop.order.OrderService;
import edu.shop.security.CurrentUser;
import edu.shop.user.Role;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 经过真实 Controller、Service、MyBatis 和数据库的业务测试。 默认使用独立 H2 库；配置 SHOP_TEST_DB_* 后可以在专用 MySQL 测试库复测。
 * 不使用 @Transactional 包住测试：工作线程必须能看见已提交的数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShopIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired OrderService orders;
  @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
  @Autowired edu.shop.queue.QueueMapper tickets;
  @Autowired edu.shop.queue.QueueService queue;

  @Test
  void failureAfterSuccessfulWritesRollsBackTheWholeOrder() throws Exception {
    long product = newProduct(5, "6");
    String username = "u" + unique();
    long member = newMember(username);
    String key = unique();
    CurrentUser sales = new CurrentUser(userId("cashier1"), "cashier1", "销售", Role.SALES);
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      orders.checkoutFor(
                          sales,
                          new OrderRequests.CheckoutRequest(
                              member, List.of(new OrderRequests.Item(product, 2)), key));
                      assertThat(stock(product)).isEqualTo(3);
                      throw new edu.shop.common.BusinessException("在所有业务写入后注入故障");
                    }))
        .isInstanceOf(edu.shop.common.BusinessException.class);
    assertThat(stock(product)).isEqualTo(5);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM shop_order WHERE request_key=?", Integer.class, key))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_log WHERE product_id=? AND type='SALE'",
                Integer.class,
                product))
        .isZero();
    assertThat(
            jdbc.queryForObject("SELECT points FROM shop_user WHERE id=?", Integer.class, member))
        .isZero();
  }

  @Test
  void startupRecoveryRecognizesPaidTicketsAndFailsOnlyUnpaidOnes() throws Exception {
    String username = "u" + unique();
    long member = newMember(username);
    long product = newProduct(3, "5");
    var paid = new edu.shop.queue.CheckoutTicket();
    paid.setMemberId(member);
    paid.setRequestKey(unique());
    paid.setPayloadJson("[]");
    tickets.insert(paid);
    long orderId =
        orders
            .checkoutQueued(
                member,
                userId("cashier1"),
                List.of(new OrderRequests.Item(product, 1)),
                "queue:" + paid.getId())
            .getId();
    var unpaid = new edu.shop.queue.CheckoutTicket();
    unpaid.setMemberId(member);
    unpaid.setRequestKey(unique());
    unpaid.setPayloadJson("[]");
    tickets.insert(unpaid);
    tickets.recoverPaid();
    tickets.recoverInterrupted();
    assertThat(tickets.ticket(paid.getId()).getStatus()).isEqualTo("COMPLETED");
    assertThat(tickets.ticket(paid.getId()).getOrderId()).isEqualTo(orderId);
    assertThat(tickets.ticket(unpaid.getId()).getStatus()).isEqualTo("FAILED");
    assertThat(stock(product)).isEqualTo(2);
  }

  @Test
  void anonymousRequestsAndWrongCsrfAreRejected() throws Exception {
    mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
    mvc.perform(get("/")).andExpect(status().isOk());
    mvc.perform(get("/app.js")).andExpect(status().isOk());
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"admin\",\"password\":\"Admin@12345\"}"))
        .andExpect(status().isForbidden());
    MockHttpSession admin = login("admin");
    mvc.perform(
            post("/api/categories")
                .session(admin)
                .contentType("application/json")
                .content("{\"name\":\"不应保存\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void realCsrfSessionLoginLogoutLifecycle() throws Exception {
    MvcResult tokenResult =
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    JsonNode token = data(tokenResult);
    MockHttpSession session = (MockHttpSession) tokenResult.getRequest().getSession(false);
    String oldId = session.getId();
    mvc.perform(
            post("/api/auth/login")
                .session(session)
                .header(token.get("headerName").asText(), token.get("token").asText())
                .contentType("application/json")
                .content("{\"username\":\"admin\",\"password\":\"Admin@12345\"}"))
        .andExpect(status().isOk());
    assertThat(session.getId()).isNotEqualTo(oldId);
    assertThat(getData("/api/auth/me", session).get("role").asText()).isEqualTo("ADMIN");
    write(post("/api/auth/logout"), session, Map.of(), 200);
    assertThat(session.isInvalid()).isTrue();
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void registerCannotChooseRoleOrExposePassword() throws Exception {
    String username = "u" + unique();
    JsonNode result =
        write(
            post("/api/auth/register"),
            null,
            Map.of(
                "username",
                username,
                "password",
                "Test@12345",
                "displayName",
                "新会员",
                "role",
                "ADMIN",
                "points",
                99999),
            200);
    MockHttpSession member = login(username, "Test@12345");
    String profile = getData("/api/auth/me", member).toString();
    assertThat(profile).contains("MEMBER").doesNotContain("password", "hash", "99999");
    assertThat(result.get("id").asLong()).isPositive();
    String hash =
        jdbc.queryForObject(
            "SELECT password_hash FROM shop_user WHERE username=?", String.class, username);
    assertThat(hash).startsWith("$2").doesNotContain("Test@12345");
    write(
        post("/api/auth/register"),
        null,
        Map.of("username", username, "password", "Test@12345", "displayName", "重复"),
        409);
  }

  @Test
  void rejectsInvalidLoginAndRegistration() throws Exception {
    write(post("/api/auth/login"), null, Map.of("username", "admin", "password", "wrong"), 401);
    write(
        post("/api/auth/register"),
        null,
        Map.of("username", "bad name", "password", "12345678", "displayName", "用户"),
        400);
    write(
        post("/api/auth/register"),
        null,
        Map.of("username", "u" + unique(), "password", "中".repeat(30), "displayName", "用户"),
        400);
    mvc.perform(
            post("/api/auth/register").with(csrf()).contentType("application/json").content("{"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void disabledUserCannotReuseSessionAndAdminCannotDisableSelf() throws Exception {
    MockHttpSession admin = login("admin");
    String username = "u" + unique();
    long memberId = newMember(username);
    MockHttpSession member = login(username, "Test@12345");
    write(patch("/api/users/" + memberId + "/enabled"), admin, Map.of("enabled", false), 200);
    mvc.perform(get("/api/auth/me").session(member)).andExpect(status().isUnauthorized());
    write(
        post("/api/auth/login"), null, Map.of("username", username, "password", "Test@12345"), 401);
    write(
        patch("/api/users/" + userId("admin") + "/enabled"), admin, Map.of("enabled", false), 400);
    write(patch("/api/users/" + memberId + "/enabled"), admin, Map.of("enabled", true), 200);
  }

  @Test
  void pendingMemberCannotBeDisabledUntilSubmissionEnds() throws Exception {
    String username = "u" + unique();
    long memberId = newMember(username);
    MockHttpSession member = login(username, "Test@12345");
    MockHttpSession admin = login("admin");
    long product = newProduct(3, "8");
    write(put("/api/cart"), member, Map.of("productId", product, "quantity", 1), 200);
    long ticket = write(post("/api/queue/join"), member, Map.of("requestKey", unique()), 200).get("id").asLong();
    try {
      write(patch("/api/users/" + memberId + "/enabled"), admin, Map.of("enabled", false), 400);
      assertThat(jdbc.queryForObject("SELECT enabled FROM shop_user WHERE id=?", Boolean.class, memberId)).isTrue();
      assertThat(getData("/api/auth/me", member).get("id").asLong()).isEqualTo(memberId);
      write(post("/api/queue/" + ticket + "/cancel"), member, Map.of(), 200);
      write(patch("/api/users/" + memberId + "/enabled"), admin, Map.of("enabled", false), 200);
      assertThat(jdbc.queryForObject("SELECT enabled FROM shop_user WHERE id=?", Boolean.class, memberId)).isFalse();
    } finally {
      jdbc.update("UPDATE checkout_ticket SET status='CANCELLED' WHERE id=? AND status='WAITING'", ticket);
    }
  }

  @Test
  void rolePermissionsAndPublicUserFields() throws Exception {
    MockHttpSession member = login("member1");
    MockHttpSession sales = login("cashier1");
    for (String path :
        List.of(
            "/api/stats",
            "/api/suppliers",
            "/api/purchases",
            "/api/users",
            "/api/audit",
            "/api/export/orders.csv")) {
      mvc.perform(get(path).session(member)).andExpect(status().isForbidden());
    }
    mvc.perform(get("/api/stats").session(sales)).andExpect(status().isForbidden());
    JsonNode users = getData("/api/users", sales);
    assertThat(users).isNotEmpty();
    for (JsonNode user : users) assertThat(user.get("role").asText()).isEqualTo("MEMBER");
    assertThat(users.toString()).doesNotContain("password", "hash");
    write(post("/api/orders/checkout"), member, checkout(1, null, unique()), 403);
    mvc.perform(get("/v3/api-docs").session(member)).andExpect(status().isForbidden());
    mvc.perform(get("/v3/api-docs").session(login("admin"))).andExpect(status().isOk());
  }

  @Test
  void categoryAndProductCrudSearchAndSaleVisibility() throws Exception {
    MockHttpSession admin = login("admin");
    String name = "分类" + unique();
    write(post("/api/categories"), admin, Map.of("name", name), 200);
    long category = jdbc.queryForObject("SELECT id FROM category WHERE name=?", Long.class, name);
    write(put("/api/categories/" + category), admin, Map.of("name", name + "改"), 200);
    Map<String, Object> body = productBody(category, "测试" + unique(), 0, "18.80");
    long product = write(post("/api/products"), admin, body, 200).get("id").asLong();
    assertThat(
            getData("/api/products?keyword=" + body.get("sku") + "&categoryId=" + category, admin))
        .hasSize(1);
    body.put("stock", 500);
    body.put("name", "改名商品");
    assertThat(write(put("/api/products/" + product), admin, body, 200).get("stock").asInt())
        .isZero();
    write(patch("/api/products/" + product + "/sale"), admin, Map.of("onSale", false), 200);
    assertThat(getData("/api/products?keyword=" + body.get("sku"), login("member1"))).isEmpty();
    assertThat(getData("/api/products?keyword=" + body.get("sku"), admin)).hasSize(1);
    write(delete("/api/categories/" + category), admin, Map.of(), 409);
    write(delete("/api/products/" + product), admin, Map.of(), 200);
    write(delete("/api/categories/" + category), admin, Map.of(), 200);
  }

  @Test
  void productValidationAndDuplicateSkuDoNotWriteInvalidData() throws Exception {
    MockHttpSession admin = login("admin");
    long category = categoryId();
    Map<String, Object> body = productBody(category, "校验" + unique(), 3, "12.00");
    write(post("/api/products"), admin, body, 200);
    write(post("/api/products"), admin, body, 409);
    body.put("price", "-1");
    write(post("/api/products"), admin, body, 400);
    body.put("price", "12.001");
    write(post("/api/products"), admin, body, 400);
    body.put("price", "12");
    body.put("stock", -1);
    write(post("/api/products"), admin, body, 400);
    write(post("/api/categories"), admin, Map.of("name", " "), 400);
    mvc.perform(get("/api/products?categoryId=abc").session(admin))
        .andExpect(status().isBadRequest());
    write(put("/api/products/9999999"), admin, productBody(category, "无商品", 0, "1"), 404);
    write(delete("/api/products/9999999"), admin, Map.of(), 404);
  }

  @Test
  void purchaseWritesDetailsAndInventoryTogether() throws Exception {
    MockHttpSession admin = login("admin");
    String supplierName = "供应" + unique();
    write(
        post("/api/suppliers"),
        admin,
        Map.of("name", supplierName, "contact", "张经理", "phone", "13800000000"),
        200);
    long supplier =
        jdbc.queryForObject("SELECT id FROM supplier WHERE name=?", Long.class, supplierName);
    write(
        put("/api/suppliers/" + supplier),
        admin,
        Map.of("name", supplierName, "contact", "李经理", "phone", "13900000000"),
        200);
    long product = newProduct(2, "20");
    JsonNode purchase =
        write(
            post("/api/purchases"),
            admin,
            Map.of(
                "supplierId",
                supplier,
                "items",
                List.of(Map.of("productId", product, "quantity", 5, "unitCost", "8.50"))),
            200);
    assertThat(purchase.get("totalAmount").decimalValue()).isEqualByComparingTo("42.50");
    assertThat(stock(product)).isEqualTo(7);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM purchase_item WHERE purchase_id=?",
                Integer.class,
                purchase.get("id").asLong()))
        .isEqualTo(1);
    assertThat(getData("/api/purchases", admin).toString()).contains(supplierName);
    assertThat(getData("/api/inventory", admin).toString()).contains("PURCHASE");
    write(delete("/api/suppliers/" + supplier), admin, Map.of(), 409);
    long before = jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order", Long.class);
    write(
        post("/api/purchases"),
        admin,
        Map.of(
            "supplierId",
            supplier,
            "items",
            List.of(
                Map.of("productId", product, "quantity", 2, "unitCost", "1"),
                Map.of("productId", 99999999, "quantity", 2, "unitCost", "1"))),
        400);
    assertThat(stock(product)).isEqualTo(7);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order", Long.class))
        .isEqualTo(before);
  }

  @Test
  void unusedSupplierCanBeDeleted() throws Exception {
    MockHttpSession admin = login("admin");
    String name = "临时" + unique();
    write(post("/api/suppliers"), admin, Map.of("name", name), 200);
    long id = jdbc.queryForObject("SELECT id FROM supplier WHERE name=?", Long.class, name);
    write(delete("/api/suppliers/" + id), admin, Map.of(), 200);
    write(delete("/api/suppliers/" + id), admin, Map.of(), 404);
    assertThat(getData("/api/suppliers", admin).toString()).doesNotContain(name);
  }

  @Test
  void checkoutRefundRestoresStockPointsAndNetSpending() throws Exception {
    MockHttpSession sales = login("cashier1");
    MockHttpSession admin = login("admin");
    String username = "u" + unique();
    long member = newMember(username);
    long product = newProduct(8, "12.50");
    JsonNode order =
        write(post("/api/orders/checkout"), sales, checkout(product, member, unique(), 2), 200);
    long orderId = order.get("id").asLong();
    assertThat(stock(product)).isEqualTo(6);
    assertThat(order.get("payableAmount").decimalValue()).isEqualByComparingTo("25");
    assertThat(
            jdbc.queryForObject("SELECT points FROM shop_user WHERE id=?", Integer.class, member))
        .isEqualTo(25);
    MockHttpSession memberSession = login(username, "Test@12345");
    assertThat(getData("/api/points", memberSession)).hasSize(1);
    write(post("/api/orders/" + orderId + "/refund-request"), memberSession, Map.of("reason", "顾客整单退货"), 200);
    write(
        post("/api/orders/" + orderId + "/refund"), login("cashier2"), Map.of("reason", "越权"), 403);
    assertThat(
            write(
                    post("/api/orders/" + orderId + "/refund-review"),
                    admin,
                    Map.of("approve", true, "reason", "确认整单退货"),
                    200)
                .get("status")
                .asText())
        .isEqualTo("REFUNDED");
    assertThat(stock(product)).isEqualTo(8);
    assertThat(
            jdbc.queryForObject("SELECT points FROM shop_user WHERE id=?", Integer.class, member))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT total_spent FROM shop_user WHERE id=?", BigDecimal.class, member))
        .isEqualByComparingTo("0");
    write(post("/api/orders/" + orderId + "/refund-review"), admin, Map.of("approve", true), 409);
    assertThat(stock(product)).isEqualTo(8);
  }

  @Test
  void orderListsAndDetailsEnforceOwnership() throws Exception {
    MockHttpSession sales = login("cashier1");
    long product = newProduct(3, "8");
    JsonNode order =
        write(
            post("/api/orders/checkout"),
            sales,
            checkout(product, userId("member1"), unique()),
            200);
    long id = order.get("id").asLong();
    assertThat(getData("/api/orders/" + id, sales).get("id").asLong()).isEqualTo(id);
    assertThat(getData("/api/orders/" + id, login("member1")).get("id").asLong()).isEqualTo(id);
    mvc.perform(get("/api/orders/" + id).session(login("member2")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/orders/" + id).session(login("cashier2")))
        .andExpect(status().isForbidden());
    assertThat(getData("/api/orders", login("cashier2")).toString())
        .doesNotContain(order.get("orderNo").asText());
    assertThat(getData("/api/orders", login("admin")).toString())
        .contains(order.get("orderNo").asText());
    mvc.perform(get("/api/orders/99999999").session(sales)).andExpect(status().isNotFound());
  }

  @Test
  void failedMultiItemCheckoutRollsBackEverything() throws Exception {
    MockHttpSession sales = login("cashier1");
    long first = newProduct(10, "3");
    long empty = newProduct(0, "4");
    String key = unique();
    write(
        post("/api/orders/checkout"),
        sales,
        Map.of(
            "requestKey",
            key,
            "items",
            List.of(
                Map.of("productId", first, "quantity", 2),
                Map.of("productId", empty, "quantity", 1))),
        400);
    assertThat(stock(first)).isEqualTo(10);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM shop_order WHERE request_key=?", Integer.class, key))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_log WHERE product_id=? AND type='SALE'",
                Integer.class,
                first))
        .isZero();
  }

  @Test
  void repeatedCheckoutIsIdempotentAndDifferentCashierCannotReuseKey() throws Exception {
    MockHttpSession sales = login("cashier1");
    long product = newProduct(5, "9");
    String key = unique();
    JsonNode first = write(post("/api/orders/checkout"), sales, checkout(product, null, key), 200);
    JsonNode again = write(post("/api/orders/checkout"), sales, checkout(product, null, key), 200);
    assertThat(again.get("id")).isEqualTo(first.get("id"));
    assertThat(stock(product)).isEqualTo(4);
    write(post("/api/orders/checkout"), login("cashier2"), checkout(product, null, key), 409);
    assertThat(stock(product)).isEqualTo(4);
  }

  @Test
  void cartAndPreviewUseServerIdentityAndPrices() throws Exception {
    String username = "u" + unique();
    long memberId = newMember(username);
    MockHttpSession member = login(username, "Test@12345");
    long product = newProduct(4, "12.50");
    write(put("/api/cart"), member, Map.of("productId", product, "quantity", 2), 200);
    assertThat(getData("/api/cart", member)).hasSize(1);
    JsonNode preview =
        write(
            post("/api/orders/preview"),
            member,
            Map.of(
                "memberId",
                userId("member2"),
                "price",
                "0.01",
                "items",
                List.of(Map.of("productId", product, "quantity", 2, "unitPrice", "0.01"))),
            200);
    assertThat(preview.get("payableAmount").decimalValue()).isEqualByComparingTo("25");
    assertThat(preview.get("earnedPoints").asInt()).isEqualTo(25);
    write(put("/api/cart"), member, Map.of("productId", product, "quantity", 5), 400);
    write(delete("/api/cart/" + product), member, Map.of(), 200);
    assertThat(getData("/api/cart", member)).isEmpty();
    assertThat(
            jdbc.queryForObject("SELECT points FROM shop_user WHERE id=?", Integer.class, memberId))
        .isZero();
  }

  @Test
  void memberSubmissionWaitsForNamedCashierAndOnlyExposesOwnTickets() throws Exception {
    String username = "u" + unique();
    long memberId = newMember(username);
    MockHttpSession member = login(username, "Test@12345");
    long product = newProduct(4, "7.50");
    write(put("/api/cart"), member, Map.of("productId", product, "quantity", 2), 200);
    String key = unique();
    JsonNode submitted = write(post("/api/queue/join"), member, Map.of("requestKey", key), 200);
    long ticket = submitted.get("id").asLong();
    String cashierName = submitted.get("cashierUsername").asText();
    assertThat(submitted.get("counterNo").asInt()).isBetween(1, 2);
    assertThat(submitted.get("cashierName").asText()).isNotBlank();
    assertThat(submitted.get("status").asText()).isEqualTo("WAITING");
    assertThat(submitted.get("items").get(0).get("quantity").asInt()).isEqualTo(2);
    // 测试配置旧自动延迟为 10 毫秒；现在等候期间必须没有付款。
    TimeUnit.MILLISECONDS.sleep(150);
    assertThat(tickets.ticket(ticket).getStatus()).isEqualTo("WAITING");
    assertThat(stock(product)).isEqualTo(4);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shop_order WHERE request_key=?", Integer.class, "queue:" + ticket)).isZero();
    assertThat(jdbc.queryForObject("SELECT points FROM shop_user WHERE id=?", Integer.class, memberId)).isZero();
    assertThat(getData("/api/cart", member)).hasSize(1);
    MockHttpSession assigned = login(cashierName);
    String otherName = cashierName.equals("cashier1") ? "cashier2" : "cashier1";
    MockHttpSession other = login(otherName);
    write(post("/api/queue/" + ticket + "/confirm"), member, Map.of(), 403);
    write(post("/api/queue/" + ticket + "/confirm"), login("admin"), Map.of(), 403);
    write(post("/api/queue/" + ticket + "/confirm"), other, Map.of(), 403);
    for (JsonNode row : getData("/api/queue", other).get("tickets"))
      assertThat(row.get("id").asLong()).isNotEqualTo(ticket);
    JsonNode confirmed = write(post("/api/queue/" + ticket + "/confirm"), assigned, Map.of(), 200);
    assertThat(confirmed.get("status").asText()).isEqualTo("COMPLETED");
    long orderId = confirmed.get("orderId").asLong();
    assertThat(write(post("/api/queue/" + ticket + "/confirm"), assigned, Map.of(), 200).get("orderId").asLong()).isEqualTo(orderId);
    assertThat(stock(product)).isEqualTo(2);
    assertThat(getData("/api/cart", member)).isEmpty();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shop_order WHERE request_key=?", Integer.class, "queue:" + ticket)).isEqualTo(1);
    JsonNode snapshot = getData("/api/queue", member);
    for (JsonNode row : snapshot.get("tickets")) assertThat(row.get("memberId").asLong()).isEqualTo(memberId);
    for (JsonNode counter : snapshot.get("counters")) assertThat(counter.get("ticketId").isNull()).isTrue();
    assertThat(write(post("/api/queue/join"), member, Map.of("requestKey", key), 200).get("id").asLong()).isEqualTo(ticket);
    write(post("/api/queue/" + ticket + "/cancel"), login("member2"), Map.of(), 403);
    write(post("/api/queue/" + ticket + "/cancel"), member, Map.of(), 400);
  }

  @Test
  void waitingSubmissionSurvivesRecoveryAndClosedCounterCanFinishItsWork() throws Exception {
    long product = newProduct(3, "5");
    String username = "u" + unique();
    newMember(username);
    MockHttpSession member = login(username, "Test@12345");
    write(put("/api/cart"), member, Map.of("productId", product, "quantity", 1), 200);
    JsonNode submitted = write(post("/api/queue/join"), member, Map.of("requestKey", unique()), 200);
    long ticket = submitted.get("id").asLong();
    jdbc.update("UPDATE checkout_ticket SET counter_no=2,cashier_id=? WHERE id=?", userId("cashier2"), ticket);
    String previous = jdbc.queryForObject("SELECT setting_value FROM shop_setting WHERE setting_key='COUNTER_COUNT'", String.class);
    try {
      jdbc.update("UPDATE shop_setting SET setting_value='1' WHERE setting_key='COUNTER_COUNT'");
      queue.start();
      assertThat(tickets.ticket(ticket).getStatus()).isEqualTo("WAITING");
      assertThat(tickets.ticket(ticket).getCounterNo()).isEqualTo(2);
      assertThat(stock(product)).isEqualTo(3);
      write(post("/api/queue/" + ticket + "/confirm"), login("cashier2"), Map.of(), 200);
      assertThat(stock(product)).isEqualTo(2);
    } finally {
      jdbc.update("UPDATE shop_setting SET setting_value=? WHERE setting_key='COUNTER_COUNT'", previous);
      jdbc.update("UPDATE checkout_ticket SET status='CANCELLED' WHERE id=? AND status='WAITING'", ticket);
    }
  }

  @Test
  void confirmationUsesSubmittedQuantitiesAndRollsBackIfStockChanged() throws Exception {
    long product = newProduct(3, "5");
    String username = "u" + unique(); newMember(username);
    MockHttpSession member = login(username, "Test@12345");
    write(put("/api/cart"), member, Map.of("productId", product, "quantity", 2), 200);
    JsonNode submitted = write(post("/api/queue/join"), member, Map.of("requestKey", unique()), 200);
    long ticket = submitted.get("id").asLong();
    MockHttpSession cashier = login(submitted.get("cashierUsername").asText());
    try {
      jdbc.update("UPDATE product SET stock=1 WHERE id=?", product);
      write(post("/api/queue/" + ticket + "/confirm"), cashier, Map.of(), 400);
      assertThat(tickets.ticket(ticket).getStatus()).isEqualTo("WAITING");
      assertThat(stock(product)).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shop_order WHERE request_key=?", Integer.class, "queue:" + ticket)).isZero();
      jdbc.update("UPDATE product SET stock=5 WHERE id=?", product);
      write(put("/api/cart"), member, Map.of("productId", product, "quantity", 3), 200);
      JsonNode done = write(post("/api/queue/" + ticket + "/confirm"), cashier, Map.of(), 200);
      assertThat(done.get("items").get(0).get("quantity").asInt()).isEqualTo(2);
      assertThat(stock(product)).isEqualTo(3);
      assertThat(getData("/api/cart", member).get(0).get("quantity").asInt()).isEqualTo(1);
    } finally {
      jdbc.update("UPDATE checkout_ticket SET status='CANCELLED' WHERE id=? AND status='WAITING'", ticket);
    }
  }

  @Test
  void cancelAndConcurrentConfirmHaveOneOutcomeAndConfirmationRetryIsSafe() throws Exception {
    long product = newProduct(5, "4");
    String username = "u" + unique();
    long memberId = newMember(username);
    MockHttpSession memberSession = login(username, "Test@12345");
    write(put("/api/cart"), memberSession, Map.of("productId", product, "quantity", 1), 200);
    JsonNode submitted = write(post("/api/queue/join"), memberSession, Map.of("requestKey", unique()), 200);
    long ticket = submitted.get("id").asLong();
    CurrentUser member = new CurrentUser(memberId, username, username, Role.MEMBER);
    CurrentUser cashier = new CurrentUser(submitted.get("cashierId").asLong(), submitted.get("cashierUsername").asText(), "销售", Role.SALES);
    var pool = Executors.newFixedThreadPool(3);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Boolean>> outcomes = new ArrayList<>();
      outcomes.add(pool.submit(() -> { start.await(); try { queue.cancel(member, ticket); return true; } catch (edu.shop.common.BusinessException ex) { return false; } }));
      for (int i=0;i<2;i++) outcomes.add(pool.submit(() -> { start.await(); try { queue.confirm(cashier, ticket); return true; } catch (edu.shop.common.BusinessException ex) { return false; } }));
      start.countDown();
      for (Future<Boolean> outcome : outcomes) outcome.get(15, TimeUnit.SECONDS);
      String state = tickets.ticket(ticket).getStatus();
      assertThat(state).isIn("COMPLETED", "CANCELLED");
      int count = jdbc.queryForObject("SELECT COUNT(*) FROM shop_order WHERE request_key=?", Integer.class, "queue:" + ticket);
      assertThat(count).isEqualTo(state.equals("COMPLETED") ? 1 : 0);
      assertThat(stock(product)).isEqualTo(state.equals("COMPLETED") ? 4 : 5);
    } finally { pool.shutdownNow(); }
  }

  @Test
  void shortestCounterAssignmentIsStableAndCounterQueueIsFifo() throws Exception {
    String previous = jdbc.queryForObject("SELECT setting_value FROM shop_setting WHERE setting_key='COUNTER_COUNT'", String.class);
    List<Long> submittedIds = new ArrayList<>();
    try {
      jdbc.update("UPDATE shop_setting SET setting_value='2' WHERE setting_key='COUNTER_COUNT'");
      List<JsonNode> submissions = new ArrayList<>();
      for (int i=0;i<3;i++) {
        String username = "u" + unique(); newMember(username);
        MockHttpSession member = login(username, "Test@12345");
        long product = newProduct(2, "3");
        write(put("/api/cart"), member, Map.of("productId", product, "quantity", 1), 200);
        JsonNode submitted = write(post("/api/queue/join"), member, Map.of("requestKey", unique()), 200);
        submissions.add(submitted); submittedIds.add(submitted.get("id").asLong());
      }
      assertThat(submissions.get(0).get("counterNo").asInt()).isEqualTo(1);
      assertThat(submissions.get(1).get("counterNo").asInt()).isEqualTo(2);
      assertThat(submissions.get(2).get("counterNo").asInt()).isEqualTo(1);
      MockHttpSession cashier = login("cashier1");
      write(post("/api/queue/" + submittedIds.get(2) + "/confirm"), cashier, Map.of(), 400);
      write(post("/api/queue/" + submittedIds.get(0) + "/confirm"), cashier, Map.of(), 200);
      write(post("/api/queue/" + submittedIds.get(2) + "/confirm"), cashier, Map.of(), 200);
      jdbc.update("UPDATE shop_user SET enabled=FALSE WHERE username='cashier1'");
      JsonNode counters = getData("/api/queue", login("admin")).get("counters");
      assertThat(counters.get(0).get("cashierUsername").asText()).isEqualTo("cashier1");
      assertThat(counters.get(0).get("status").asText()).isEqualTo("UNAVAILABLE");
      assertThat(counters.get(1).get("cashierUsername").asText()).isEqualTo("cashier2");
    } finally {
      jdbc.update("UPDATE shop_user SET enabled=TRUE WHERE username='cashier1'");
      jdbc.update("UPDATE shop_setting SET setting_value=? WHERE setting_key='COUNTER_COUNT'", previous);
      for (long id : submittedIds) jdbc.update("UPDATE checkout_ticket SET status='CANCELLED' WHERE id=? AND status='WAITING'", id);
    }
  }

  @Test
  void actualConcurrentTransactionsNeverOversell() throws Exception {
    long product = newProduct(5, "2");
    CurrentUser cashier = new CurrentUser(userId("cashier1"), "cashier1", "销售", Role.SALES);
    var pool = Executors.newFixedThreadPool(8);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Boolean>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < 12; i++)
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    orders.checkoutFor(
                        cashier,
                        new OrderRequests.CheckoutRequest(
                            null, List.of(new OrderRequests.Item(product, 1)), unique()));
                    return true;
                  } catch (edu.shop.common.BusinessException expected) {
                    return false;
                  }
                }));
      start.countDown();
      int successes = 0;
      for (Future<Boolean> future : futures) if (future.get(20, TimeUnit.SECONDS)) successes++;
      assertThat(successes).isEqualTo(5);
      assertThat(stock(product)).isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM order_item WHERE product_id=?", Integer.class, product))
          .isEqualTo(5);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void concurrentDuplicateKeyAndRefundEachApplyOnlyOnce() throws Exception {
    long product = newProduct(5, "3");
    String key = unique();
    CurrentUser actor = new CurrentUser(userId("cashier1"), "cashier1", "销售", Role.SALES);
    String memberName = "u" + unique();
    long memberId = newMember(memberName);
    CurrentUser memberActor = new CurrentUser(memberId, memberName, "会员", Role.MEMBER);
    CurrentUser adminActor = new CurrentUser(userId("admin"), "admin", "老板", Role.ADMIN);
    var request =
        new OrderRequests.CheckoutRequest(memberId, List.of(new OrderRequests.Item(product, 1)), key);
    var pool = Executors.newFixedThreadPool(4);
    try {
      List<Callable<Long>> calls = new ArrayList<>();
      for (int i = 0; i < 4; i++) calls.add(() -> orders.checkoutFor(actor, request).getId());
      List<Future<Long>> results = pool.invokeAll(calls);
      long id = results.getFirst().get();
      for (var future : results) assertThat(future.get()).isEqualTo(id);
      assertThat(stock(product)).isEqualTo(4);
      orders.requestRefund(memberActor, id, "申请整单退款");
      List<Callable<Boolean>> refunds = new ArrayList<>();
      for (int i = 0; i < 4; i++)
        refunds.add(
            () -> {
              try {
                orders.reviewRefund(adminActor, id, true, "并发退货测试");
                return true;
              } catch (edu.shop.common.BusinessException expected) {
                return false;
              }
            });
      int refunded = 0;
      for (var future : pool.invokeAll(refunds)) if (future.get()) refunded++;
      assertThat(refunded).isEqualTo(1);
      assertThat(stock(product)).isEqualTo(5);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void statisticsSettingsAuditAndCsvWorkAgainstDatabase() throws Exception {
    MockHttpSession admin = login("admin");
    JsonNode settings = getData("/api/settings", admin);
    write(
        put("/api/settings"),
        admin,
        Map.of(
            "productDiscountRate",
            "0.90",
            "fullReductionThreshold",
            "100",
            "fullReductionAmount",
            "10",
            "counterCount",
            3),
        200);
    assertThat(getData("/api/settings", admin).get("counterCount").asInt()).isEqualTo(3);
    write(
        put("/api/settings"),
        admin,
        Map.of(
            "productDiscountRate",
            "1",
            "fullReductionThreshold",
            "10",
            "fullReductionAmount",
            "20",
            "counterCount",
            2),
        400);
    write(put("/api/settings"), admin, json.convertValue(settings, Map.class), 200);
    JsonNode stats = getData("/api/stats", admin);
    for (String field :
        List.of(
            "todaySales",
            "totalSales",
            "paidOrderCount",
            "memberCount",
            "productCount",
            "lowStock",
            "dailySales",
            "hotProducts",
            "cashierPerformance")) assertThat(stats.has(field)).as(field).isTrue();
    assertThat(getData("/api/audit", admin)).isNotEmpty();
    MvcResult csv =
        mvc.perform(get("/api/export/orders.csv").session(admin))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(csv.getResponse().getContentAsByteArray())
        .startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
    assertThat(csv.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
        .contains("订单号")
        .doesNotContain("password_hash");
  }

  @Test
  void digitalSeedCatalogAndOwnerMatchTheNewStore() throws Exception {
    MockHttpSession admin = login("admin");
    assertThat(getData("/api/auth/me", admin).get("displayName").asText()).isEqualTo("乔胤淇");
    JsonNode products = getData("/api/products", admin);
    assertThat(products.toString()).contains("PHONE-001", "PC-001", "WATCH-001", "GPU-001");
    assertThat(products.toString()).doesNotContain("牧场鲜牛奶", "黄油可颂", "彩色便利贴");
    assertThat(getData("/api/categories", admin).toString())
        .contains("手机通讯", "电脑整机", "智能穿戴", "显卡硬件");
  }

  @Test
  void legacyFoodOrderRemainsReadableButCannotRestoreDigitalStock() throws Exception {
    MockHttpSession sales = login("cashier1");
    String memberName = "u" + unique();
    long member = newMember(memberName);
    MockHttpSession memberSession = login(memberName, "Test@12345");
    long product = newProduct(4, "12.00");
    JsonNode created =
        write(post("/api/orders/checkout"), sales, checkout(product, member, unique()), 200);
    long orderId = created.get("id").asLong();

    // 在测试库还原 V2/V3 后的旧记录：成交名称仍是食品，商品编号已指向电脑，旧 SKU 为空。
    jdbc.update(
        "UPDATE order_item SET product_name='黄油可颂',sku='',description='',image_url='' WHERE order_id=?",
        orderId);
    jdbc.update(
        "UPDATE product SET name='联想 ThinkBook 14',price=4599.00 WHERE id=?", product);

    Map<String, Object> memberBefore =
        jdbc.queryForMap("SELECT points,total_spent FROM shop_user WHERE id=?", member);
    int stockBefore = stock(product);
    int inventoryBefore =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM inventory_log WHERE product_id=?", Integer.class, product);
    int pointsBefore =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM point_log WHERE order_id=?", Integer.class, orderId);

    JsonNode historical = getData("/api/orders/" + orderId, sales);
    assertThat(historical.get("legacyDemoOrder").asBoolean()).isTrue();
    assertThat(historical.get("items").get(0).get("legacyDemoItem").asBoolean()).isTrue();
    assertThat(historical.get("items").get(0).get("productName").asText()).isEqualTo("黄油可颂");
    assertThat(historical.get("items").get(0).get("unitPrice").decimalValue())
        .isEqualByComparingTo("12.00");

    MvcResult denied =
        mvc.perform(
                post("/api/orders/" + orderId + "/refund-request")
                    .session(memberSession)
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of("reason", "退货"))))
            .andExpect(status().isConflict())
            .andReturn();
    assertThat(json.readTree(denied.getResponse().getContentAsByteArray()).get("message").asText())
        .contains("改版前", "不能退回当前数码商品库存");

    assertThat(stock(product)).isEqualTo(stockBefore);
    assertThat(jdbc.queryForMap("SELECT points,total_spent FROM shop_user WHERE id=?", member))
        .isEqualTo(memberBefore);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_log WHERE product_id=?", Integer.class, product))
        .isEqualTo(inventoryBefore);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM point_log WHERE order_id=?", Integer.class, orderId))
        .isEqualTo(pointsBefore);
    assertThat(jdbc.queryForObject("SELECT status FROM shop_order WHERE id=?", String.class, orderId))
        .isEqualTo("PAID");
    assertThat(getData("/api/orders/" + orderId, sales).get("items").get(0).get("productName").asText())
        .isEqualTo("黄油可颂");
  }

  @Test
  void ordinaryProductRenameAndPriceChangeStillAllowRefundOfTheOriginalOrder() throws Exception {
    MockHttpSession sales = login("cashier1");
    String memberName = "u" + unique();
    long member = newMember(memberName);
    MockHttpSession memberSession = login(memberName, "Test@12345");
    long product = newProduct(4, "12.00");
    JsonNode created =
        write(post("/api/orders/checkout"), sales, checkout(product, member, unique()), 200);
    long orderId = created.get("id").asLong();
    String originalName = created.get("items").get(0).get("productName").asText();
    jdbc.update("UPDATE product SET name='改名后的设备',price=99.00 WHERE id=?", product);

    assertThat(getData("/api/orders/" + orderId, sales).get("legacyDemoOrder").asBoolean()).isFalse();
    write(post("/api/orders/" + orderId + "/refund-request"), memberSession, Map.of("reason", "正常退货"), 200);
    JsonNode refunded =
        write(post("/api/orders/" + orderId + "/refund-review"), login("admin"), Map.of("approve", true), 200);
    assertThat(refunded.get("status").asText()).isEqualTo("REFUNDED");
    assertThat(refunded.get("items").get(0).get("productName").asText()).isEqualTo(originalName);
    assertThat(refunded.get("items").get(0).get("unitPrice").decimalValue())
        .isEqualByComparingTo("12.00");
    assertThat(stock(product)).isEqualTo(4);
    assertThat(jdbc.queryForObject("SELECT points FROM shop_user WHERE id=?", Integer.class, member))
        .isZero();
    assertThat(
            jdbc.queryForObject("SELECT total_spent FROM shop_user WHERE id=?", BigDecimal.class, member))
        .isEqualByComparingTo("0.00");
  }

  @Test
  void loginPagePolishStylesAreAvailableBeforeAuthentication() throws Exception {
    mvc.perform(get("/polish.css"))
        .andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
            .contentTypeCompatibleWith("text/css"));
  }

  private MockHttpSession login(String username) throws Exception {
    String password =
        username.equals("admin")
            ? "Admin@12345"
            : username.startsWith("cashier") ? "Sales@12345" : "Member@12345";
    return login(username, password);
  }

  private MockHttpSession login(String username, String password) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of("username", username, "password", password))))
            .andExpect(status().isOk())
            .andReturn();
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private JsonNode getData(String path, MockHttpSession session) throws Exception {
    return data(mvc.perform(get(path).session(session)).andExpect(status().isOk()).andReturn());
  }

  private JsonNode write(
      MockHttpServletRequestBuilder request, MockHttpSession session, Object body, int expected)
      throws Exception {
    if (session != null) request.session(session);
    MvcResult result =
        mvc.perform(
                request
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(body)))
            .andExpect(status().is(expected))
            .andReturn();
    return data(result);
  }

  private JsonNode data(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsByteArray()).path("data");
  }

  private long userId(String username) {
    return jdbc.queryForObject("SELECT id FROM shop_user WHERE username=?", Long.class, username);
  }

  private long categoryId() {
    return jdbc.queryForObject("SELECT MIN(id) FROM category", Long.class);
  }

  private int stock(long product) {
    return jdbc.queryForObject("SELECT stock FROM product WHERE id=?", Integer.class, product);
  }

  private String unique() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
  }

  private long newMember(String username) throws Exception {
    return write(
            post("/api/auth/register"),
            null,
            Map.of("username", username, "password", "Test@12345", "displayName", "测试会员"),
            200)
        .get("id")
        .asLong();
  }

  private long newProduct(int stock, String price) throws Exception {
    return write(
            post("/api/products"),
            login("admin"),
            productBody(categoryId(), "测试" + unique(), stock, price),
            200)
        .get("id")
        .asLong();
  }

  private Map<String, Object> productBody(long categoryId, String name, int stock, String price) {
    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("categoryId", categoryId);
    body.put("sku", "TEST-" + unique());
    body.put("name", name);
    body.put("description", "集成测试商品");
    body.put("price", price);
    body.put("stock", stock);
    body.put("lowStockThreshold", 2);
    body.put("onSale", true);
    body.put("emoji", "🧪");
    return body;
  }

  private Map<String, Object> checkout(long product, Long memberId, String key) {
    return checkout(product, memberId, key, 1);
  }

  private Map<String, Object> checkout(long product, Long memberId, String key, int quantity) {
    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("memberId", memberId);
    body.put("requestKey", key);
    body.put("items", List.of(Map.of("productId", product, "quantity", quantity)));
    return body;
  }
}

