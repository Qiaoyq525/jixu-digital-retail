package edu.shop;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.transaction.annotation.Transactional;

/** 通过真实权限、SQL 和事务验证员工名额与柜台管理；只使用独立测试数据库。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CashierIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  @Test
  @Transactional
  void onlyAdminCanListCreateOrArrangeCashiers() throws Exception {
    mvc.perform(get("/api/cashiers")).andExpect(status().isUnauthorized());
    for (String name : List.of("member1", "cashier1")) {
      MockHttpSession session = login(name);
      mvc.perform(get("/api/cashiers").session(session)).andExpect(status().isForbidden());
      write(post("/api/cashiers"), session, body(unique(), 3), 403);
      write(put("/api/cashiers/2/counter"), session, Map.of("counterNo", 3), 403);
      write(patch("/api/cashiers/2/enabled"), session, Map.of("enabled", false), 403);
    }
    mvc.perform(post("/api/cashiers").session(login("admin"))
        .contentType("application/json").content(json.writeValueAsString(body(unique(), 3))))
        .andExpect(status().isForbidden());
    assertThat(cashierCount()).isEqualTo(2);
  }

  @Test
  @Transactional
  void createdCashierHasFixedRoleHashedPasswordAndCanConfirmOwnQueue() throws Exception {
    MockHttpSession admin = login("admin");
    String username = unique();
    Map<String, Object> request = body(username, 3);
    request.put("role", "ADMIN");
    request.put("points", 99999);
    JsonNode created = write(post("/api/cashiers"), admin, request, 200);
    long cashierId = created.path("id").asLong();
    assertThat(created.path("counterNo").asInt()).isEqualTo(3);
    assertThat(created.toString()).doesNotContain("password", "hash", "Staff@12345");
    assertThat(jdbc.queryForObject("SELECT password_hash FROM shop_user WHERE id=?", String.class, cashierId))
        .startsWith("$2").isNotEqualTo("Staff@12345");
    assertThat(jdbc.queryForObject("SELECT role FROM shop_user WHERE id=?", String.class, cashierId)).isEqualTo("SALES");
    assertThat(jdbc.queryForObject("SELECT points FROM shop_user WHERE id=?", Integer.class, cashierId)).isZero();
    assertThat(setting()).isEqualTo("3");
    MockHttpSession cashier = login(username, "Staff@12345");
    assertThat(getData("/api/auth/me", cashier).path("role").asText()).isEqualTo("SALES");
    JsonNode list = getData("/api/cashiers", admin);
    assertThat(list.path("total").asInt()).isEqualTo(3);
    assertThat(list.path("limit").asInt()).isEqualTo(4);
    assertThat(list.toString()).doesNotContain("password", "hash", "Staff@12345");

    // 让新员工成为本次验证的唯一在岗收银员，再走真实购物车和排队付款。
    write(patch("/api/cashiers/" + userId("cashier1") + "/enabled"), admin, Map.of("enabled", false), 200);
    write(patch("/api/cashiers/" + userId("cashier2") + "/enabled"), admin, Map.of("enabled", false), 200);
    String memberName = unique();
    write(post("/api/auth/register"), null,
        Map.of("username", memberName, "password", "Member@12345", "displayName", "排队测试会员"), 200);
    MockHttpSession member = login(memberName, "Member@12345");
    long product = jdbc.queryForObject("SELECT id FROM product WHERE sku='MOUSE-001'", Long.class);
    int stock = jdbc.queryForObject("SELECT stock FROM product WHERE id=?", Integer.class, product);
    write(put("/api/cart"), member, Map.of("productId", product, "quantity", 1), 200);
    JsonNode ticket = write(post("/api/queue/join"), member, Map.of("requestKey", unique()), 200);
    assertThat(ticket.path("cashierId").asLong()).isEqualTo(cashierId);
    assertThat(ticket.path("counterNo").asInt()).isEqualTo(3);
    JsonNode confirmed = write(post("/api/queue/" + ticket.path("id").asLong() + "/confirm"), cashier, Map.of(), 200);
    assertThat(confirmed.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(confirmed.path("orderId").asLong()).isPositive();
    assertThat(jdbc.queryForObject("SELECT stock FROM product WHERE id=?", Integer.class, product)).isEqualTo(stock - 1);
  }

  @Test
  @Transactional
  void invalidNamesPasswordsCountersAndDuplicatesDoNotCreateEmployees() throws Exception {
    MockHttpSession admin = login("admin");
    write(post("/api/cashiers"), admin, body("member1", 3), 409);
    write(post("/api/cashiers"), admin, body("bad name", 3), 400);
    write(post("/api/cashiers"), admin, body("demo_forbidden", 3), 400);
    write(post("/api/cashiers"), admin, body(unique(), 5), 400);
    write(post("/api/cashiers"), admin, body(unique(), 1), 409);
    Map<String, Object> shortPassword = body(unique(), 3);
    shortPassword.put("password", "short");
    write(post("/api/cashiers"), admin, shortPassword, 400);
    shortPassword.put("password", "密".repeat(25));
    write(post("/api/cashiers"), admin, shortPassword, 400);
    Map<String, Object> blankName = body(unique(), 3);
    blankName.put("displayName", "  ");
    write(post("/api/cashiers"), admin, blankName, 400);
    write(put("/api/cashiers/" + userId("member1") + "/counter"), admin, Map.of("counterNo", 3), 404);
    write(patch("/api/cashiers/" + userId("cashier1") + "/enabled"), admin, Map.of(), 400);
    assertThat(cashierCount()).isEqualTo(2);
  }

  @Test
  @Transactional
  void movingFreeCashierWorksAndQueuedWorkBlocksMoveAndBothDisableRoutes() throws Exception {
    MockHttpSession admin = login("admin");
    long cashier = userId("cashier1");
    write(put("/api/cashiers/" + cashier + "/counter"), admin, Map.of("counterNo", 2), 409);
    write(put("/api/cashiers/" + cashier + "/counter"), admin, Map.of("counterNo", 4), 200);
    assertThat(jdbc.queryForObject("SELECT cashier_id FROM checkout_counter WHERE counter_no=1", Long.class)).isNull();
    assertThat(jdbc.queryForObject("SELECT cashier_id FROM checkout_counter WHERE counter_no=4", Long.class)).isEqualTo(cashier);
    assertThat(setting()).isEqualTo("4");
    jdbc.update("INSERT INTO checkout_ticket(member_id,cashier_id,counter_no,status,payload_json,request_key) VALUES(?,?,4,'WAITING','[]',?)",
        userId("member1"), cashier, unique());
    write(put("/api/cashiers/" + cashier + "/counter"), admin, Map.of("counterNo", 3), 409);
    write(patch("/api/cashiers/" + cashier + "/enabled"), admin, Map.of("enabled", false), 409);
    write(patch("/api/users/" + cashier + "/enabled"), admin, Map.of("enabled", false), 409);
    assertThat(jdbc.queryForObject("SELECT enabled FROM shop_user WHERE id=?", Boolean.class, cashier)).isTrue();
    assertThat(jdbc.queryForObject("SELECT counter_no FROM checkout_counter WHERE cashier_id=?", Integer.class, cashier)).isEqualTo(4);
    long ticketId = jdbc.queryForObject("SELECT id FROM checkout_ticket WHERE cashier_id=? AND status='WAITING'", Long.class, cashier);
    write(post("/api/queue/" + ticketId + "/cancel"), login("member1"), Map.of(), 200);
    write(patch("/api/users/" + cashier + "/enabled"), admin, Map.of("enabled", false), 200);
    write(patch("/api/cashiers/" + cashier + "/enabled"), admin, Map.of("enabled", true), 200);
    assertThat(jdbc.queryForObject("SELECT enabled FROM shop_user WHERE id=?", Boolean.class, cashier)).isTrue();
  }

  @Test
  @Transactional
  void disabledAccountsStillOccupyOneOfFourPlaces() throws Exception {
    MockHttpSession admin = login("admin");
    JsonNode third = write(post("/api/cashiers"), admin, body(unique(), 3), 200);
    write(post("/api/cashiers"), admin, body(unique(), 4), 200);
    write(patch("/api/cashiers/" + third.path("id").asLong() + "/enabled"), admin, Map.of("enabled", false), 200);
    write(post("/api/cashiers"), admin, body(unique(), 3), 409);
    assertThat(cashierCount()).isEqualTo(4);
  }

  @Test
  void concurrentCreationCannotConsumeTheLastPlaceTwice() throws Exception {
    MockHttpSession admin = login("admin");
    String originalCount = setting();
    long originalCashier = userId("cashier1");
    List<String> createdNames = List.of(unique(), unique(), unique());
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      write(post("/api/cashiers"), admin, body(createdNames.get(0), 3), 200);
      // 两个空柜台、只剩一个人员名额；确保上限测试不只是柜台唯一键碰撞。
      jdbc.update("UPDATE checkout_counter SET cashier_id=NULL WHERE counter_no=1");
      CountDownLatch ready = new CountDownLatch(2);
      CountDownLatch start = new CountDownLatch(1);
      List<Future<Integer>> results = new ArrayList<>();
      for (int i = 1; i <= 2; i++) {
        final int index = i;
        results.add(pool.submit(() -> {
          ready.countDown();
          assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
          return mvc.perform(post("/api/cashiers").session(admin).with(csrf()).contentType("application/json")
              .content(json.writeValueAsString(body(createdNames.get(index), index == 1 ? 1 : 4))))
              .andReturn().getResponse().getStatus();
        }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> result : results) statuses.add(result.get(20, TimeUnit.SECONDS));
      assertThat(statuses).containsExactlyInAnyOrder(200, 409);
      assertThat(cashierCount()).isEqualTo(4);
    } finally {
      pool.shutdownNow();
      assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
      for (String username : createdNames) {
        jdbc.update("UPDATE checkout_counter SET cashier_id=NULL WHERE cashier_id IN (SELECT id FROM shop_user WHERE username=?)", username);
        jdbc.update("DELETE FROM shop_user WHERE username=?", username);
      }
      jdbc.update("UPDATE checkout_counter SET cashier_id=? WHERE counter_no=1", originalCashier);
      jdbc.update("UPDATE shop_setting SET setting_value=? WHERE setting_key='COUNTER_COUNT'", originalCount);
    }
  }

  private Map<String, Object> body(String username, int counterNo) {
    return new LinkedHashMap<>(Map.of("username", username, "password", "Staff@12345",
        "displayName", "新收银员", "counterNo", counterNo));
  }

  private MockHttpSession login(String username) throws Exception {
    return login(username, username.equals("admin") ? "Admin@12345" : username.startsWith("cashier") ? "Sales@12345" : "Member@12345");
  }

  private MockHttpSession login(String username, String password) throws Exception {
    MvcResult result = mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
        .andExpect(status().isOk()).andReturn();
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private JsonNode getData(String path, MockHttpSession session) throws Exception {
    return data(mvc.perform(get(path).session(session)).andExpect(status().isOk()).andReturn());
  }

  private JsonNode write(MockHttpServletRequestBuilder request, MockHttpSession session, Object body, int expected) throws Exception {
    if (session != null) request.session(session);
    return data(mvc.perform(request.with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
        .andExpect(status().is(expected)).andReturn());
  }

  private JsonNode data(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsByteArray()).path("data");
  }

  private long userId(String username) { return jdbc.queryForObject("SELECT id FROM shop_user WHERE username=?", Long.class, username); }
  private int cashierCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM shop_user WHERE role='SALES'", Integer.class); }
  private String setting() { return jdbc.queryForObject("SELECT setting_value FROM shop_setting WHERE setting_key='COUNTER_COUNT'", String.class); }
  private String unique() { return "staff_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
}
