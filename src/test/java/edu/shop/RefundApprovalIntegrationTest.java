package edu.shop;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.shop.common.BusinessException;
import edu.shop.order.OrderService;
import edu.shop.security.CurrentUser;
import edu.shop.user.Role;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
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

/** 真实数据库验证退款审批权限、状态变化、回滚及竞争请求。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefundApprovalIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired OrderService orders;

  private record Purchase(long orderId, long productId, long memberId, MockHttpSession member) {}

  private Purchase purchase() throws Exception {
    String username = "u" + unique();
    long member = newMember(username);
    long product = newProduct(5, "12.50");
    long order = write(post("/api/orders/checkout"), login("cashier1"), checkout(product, member, unique()), 200).get("id").asLong();
    return new Purchase(order, product, member, login(username, "Test@12345"));
  }

  private String requestPath(Purchase p) { return "/api/orders/" + p.orderId() + "/refund-request"; }
  private String reviewPath(Purchase p) { return "/api/orders/" + p.orderId() + "/refund-review"; }
  private Map<String, Object> balance(Purchase p) {
    return jdbc.queryForMap("SELECT points,total_spent FROM shop_user WHERE id=?", p.memberId());
  }

  @Test
  @org.springframework.transaction.annotation.Transactional
  void adminRefundInboxIncludesOlderRequestsBeyondOrdinaryOrderLimit() throws Exception {
    Purchase p = purchase();
    write(post(requestPath(p)), p.member(), Map.of("reason", "较早订单的退款申请"), 200);
    for (int i = 0; i < 501; i++) {
      String key = "later-" + unique();
      jdbc.update("""
          INSERT INTO shop_order(order_no,member_id,cashier_id,status,original_amount,product_discount,
            member_discount,full_reduction,payable_amount,earned_points,discount_description,request_key)
          SELECT ?,member_id,cashier_id,status,original_amount,product_discount,
            member_discount,full_reduction,payable_amount,earned_points,discount_description,?
          FROM shop_order WHERE id=?
          """, key, key, p.orderId());
    }
    MockHttpSession admin = login("admin");
    List<Long> recent = new ArrayList<>();
    getData("/api/orders", admin).forEach(row -> recent.add(row.path("id").asLong()));
    assertThat(recent).hasSize(500).doesNotContain(p.orderId());
    List<Long> pending = new ArrayList<>();
    getData("/api/refunds", admin).forEach(row -> pending.add(row.path("id").asLong()));
    assertThat(pending).contains(p.orderId());
    for (MockHttpSession denied : List.of(p.member(), login("cashier1")))
      mvc.perform(get("/api/refunds").session(denied)).andExpect(status().isForbidden());
    mvc.perform(get("/api/refunds")).andExpect(status().isUnauthorized());
  }

  @Test
  void requestRequiresLoggedInOwnerAndMeaningfulReason() throws Exception {
    Purchase p = purchase();
    Map<String,Object> before = balance(p);
    write(post(requestPath(p)), null, Map.of("reason", "退货"), 401);
    write(post(requestPath(p)), login("admin"), Map.of("reason", "退货"), 403);
    write(post(requestPath(p)), login("cashier1"), Map.of("reason", "退货"), 403);
    write(post(requestPath(p)), login("member1"), Map.of("reason", "退货"), 403);
    write(post(requestPath(p)), p.member(), Map.of("reason", "   "), 400);
    write(post(requestPath(p)), p.member(), Map.of("reason", "x".repeat(201)), 400);
    JsonNode request = write(post(requestPath(p)), p.member(), Map.of("reason", "  型号不适合  "), 200);
    assertThat(request.get("refundStatus").asText()).isEqualTo("PENDING");
    assertThat(request.get("refundReason").asText()).isEqualTo("型号不适合");
    assertThat(request.get("status").asText()).isEqualTo("PAID");
    assertThat(request.get("refundRequestedAt").isNull()).isFalse();
    write(post(requestPath(p)), p.member(), Map.of("reason", "再次申请"), 409);
    assertThat(stock(p.productId())).isEqualTo(4);
    assertThat(balance(p)).isEqualTo(before);
  }

  @Test
  void rejectionPreservesPaymentAndIsVisibleThenMemberCanApplyAgain() throws Exception {
    Purchase p = purchase();
    Map<String,Object> before = balance(p);
    write(post(requestPath(p)), p.member(), Map.of("reason", "申请退货"), 200);
    write(post(reviewPath(p)), login("admin"), Map.of("approve", false, "reason", " "), 400);
    JsonNode rejected = write(post(reviewPath(p)), login("admin"), Map.of("approve", false, "reason", "请先核对设备序列号"), 200);
    assertThat(rejected.get("refundStatus").asText()).isEqualTo("REJECTED");
    assertThat(rejected.get("status").asText()).isEqualTo("PAID");
    for (MockHttpSession session : List.of(p.member(), login("cashier1"))) {
      JsonNode visible = getData("/api/orders/" + p.orderId(), session);
      assertThat(visible.get("refundReviewNote").asText()).isEqualTo("请先核对设备序列号");
      assertThat(visible.get("refundReviewedAt").isNull()).isFalse();
    }
    assertThat(stock(p.productId())).isEqualTo(4);
    assertThat(balance(p)).isEqualTo(before);
    JsonNode retry = write(post(requestPath(p)), p.member(), Map.of("reason", "已提供序列号，请重新核对"), 200);
    assertThat(retry.get("refundStatus").asText()).isEqualTo("PENDING");
    assertThat(retry.get("refundReviewNote").asText()).isEmpty();
    assertThat(retry.get("refundReviewedAt").isNull()).isTrue();
    JsonNode approved = write(post(reviewPath(p)), login("admin"), Map.of("approve", true, "reason", "设备已核对"), 200);
    assertThat(approved.get("status").asText()).isEqualTo("REFUNDED");
    assertThat(approved.get("refundStatus").asText()).isEqualTo("APPROVED");
    assertThat(approved.get("refundReviewNote").asText()).isEqualTo("设备已核对");
    assertThat(stock(p.productId())).isEqualTo(5);
    assertThat(jdbc.queryForObject("SELECT points FROM shop_user WHERE id=?", Integer.class, p.memberId())).isZero();
    assertThat(jdbc.queryForObject("SELECT total_spent FROM shop_user WHERE id=?", BigDecimal.class, p.memberId())).isEqualByComparingTo("0");
    write(post(requestPath(p)), p.member(), Map.of("reason", "重复"), 409);
    write(post(reviewPath(p)), login("admin"), Map.of("approve", true), 409);
    write(post(reviewPath(p)), login("admin"), Map.of("approve", false, "reason", "重复审核"), 409);
  }

  @Test
  void onlyAdminCanReviewPendingApplicationAndOldRefundEndpointCannotBypassIt() throws Exception {
    Purchase p = purchase();
    MockHttpSession admin = login("admin");
    write(post(reviewPath(p)), admin, Map.of("approve", true), 409);
    write(post(requestPath(p)), p.member(), Map.of("reason", "退货"), 200);
    write(post(reviewPath(p)), null, Map.of("approve", true), 401);
    for (MockHttpSession session : List.of(p.member(), login("cashier1"), login("cashier2"))) {
      write(post(reviewPath(p)), session, Map.of("approve", true), 403);
      write(post(reviewPath(p)), session, Map.of("approve", false, "reason", "拒绝"), 403);
    }
    for (MockHttpSession session : List.of(admin, p.member(), login("cashier1")))
      write(post("/api/orders/" + p.orderId() + "/refund"), session, Map.of("reason", "绕过审批"), 403);
    write(post(reviewPath(p)), admin, Map.of("reason", "未指定决定"), 400);
    write(post(reviewPath(p)), admin, Map.of("approve", true, "reason", "x".repeat(201)), 400);
    mvc.perform(post(reviewPath(p)).session(admin).contentType("application/json").content("{\"approve\":true}"))
        .andExpect(status().isForbidden());
    assertThat(stock(p.productId())).isEqualTo(4);
    assertThat(getData("/api/orders/" + p.orderId(), p.member()).get("refundStatus").asText()).isEqualTo("PENDING");
  }

  @Test
  void failedApprovalRollsBackStockOrderAndDecisionTogether() throws Exception {
    Purchase p = purchase();
    write(post(requestPath(p)), p.member(), Map.of("reason", "退货"), 200);
    // 审核事务先恢复库存再检查会员余额；制造余额异常，验证前面的写入也会回滚。
    jdbc.update("UPDATE shop_user SET points=0,total_spent=0 WHERE id=?", p.memberId());
    write(post(reviewPath(p)), login("admin"), Map.of("approve", true), 400);
    assertThat(stock(p.productId())).isEqualTo(4);
    JsonNode order = getData("/api/orders/" + p.orderId(), p.member());
    assertThat(order.get("status").asText()).isEqualTo("PAID");
    assertThat(order.get("refundStatus").asText()).isEqualTo("PENDING");
    assertThat(order.get("refundReviewedAt").isNull()).isTrue();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_log WHERE product_id=? AND type='REFUND'", Integer.class, p.productId())).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM point_log WHERE order_id=? AND type='REFUND'", Integer.class, p.orderId())).isZero();
  }

  @Test
  void concurrentApprovalAndRejectionHaveExactlyOneOutcome() throws Exception {
    Purchase p = purchase();
    write(post(requestPath(p)), p.member(), Map.of("reason", "申请退货"), 200);
    CurrentUser admin = new CurrentUser(userId("admin"), "admin", "老板", Role.ADMIN);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Boolean>> futures = new ArrayList<>();
      for (boolean approve : List.of(true, false)) futures.add(pool.submit(() -> {
        start.await();
        try { orders.reviewRefund(admin, p.orderId(), approve, "核对结果"); return true; }
        catch (BusinessException expected) { assertThat(expected.getStatus()).isEqualTo(409); return false; }
      }));
      start.countDown();
      int success = 0;
      for (Future<Boolean> future : futures) if (future.get(15, TimeUnit.SECONDS)) success++;
      assertThat(success).isEqualTo(1);
      JsonNode order = getData("/api/orders/" + p.orderId(), p.member());
      boolean approved = "APPROVED".equals(order.get("refundStatus").asText());
      assertThat(order.get("refundStatus").asText()).isIn("APPROVED", "REJECTED");
      assertThat(order.get("status").asText()).isEqualTo(approved ? "REFUNDED" : "PAID");
      assertThat(stock(p.productId())).isEqualTo(approved ? 5 : 4);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM point_log WHERE order_id=? AND type='REFUND'", Integer.class, p.orderId())).isEqualTo(approved ? 1 : 0);
    } finally { pool.shutdownNow(); }
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

