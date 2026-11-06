package edu.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 核对实物图、规格从商品到购物车、试算和数据库订单快照的完整映射。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductImagesIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  @Test
  void catalogIncludesAll14LocalImagesAndPeripherals() throws Exception {
    MockHttpSession member = login("member1", "Member@12345");
    JsonNode catalog = getData("/api/products", member);
    for (String sku : new String[] {"PHONE-001", "PHONE-002", "PHONE-003", "PHONE-004",
        "PC-001", "PC-002", "PC-003", "WATCH-001", "WATCH-002", "GPU-001", "GPU-002", "GPU-003", "MOUSE-001", "KEY-001"}) {
      JsonNode product = findSku(catalog, sku);
      String path = "/images/products/" + sku + ".webp";
      assertThat(product.path("imageUrl").asText()).isEqualTo(path);
      assertThat(new ClassPathResource("static" + path).contentLength()).isGreaterThan(1000);
    }
    assertThat(findSku(catalog, "MOUSE-001").path("price").decimalValue()).isEqualByComparingTo("199");
    assertThat(findSku(catalog, "KEY-001").path("price").decimalValue()).isEqualByComparingTo("249");
    assertThat(getData("/api/categories", member).toString()).contains("键鼠外设");
    assertThat(getData("/api/products?keyword=HERO", member).toString()).contains("MOUSE-001");
  }

  @Test
  void orderSpecificationsRemainThePurchaseSnapshotAfterProductIsEdited() throws Exception {
    MockHttpSession admin = login("admin", "Admin@12345");
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    long category = jdbc.queryForObject("SELECT MIN(id) FROM category", Long.class);
    JsonNode created = json.readTree(mvc.perform(post("/api/products").session(admin).with(csrf())
        .contentType("application/json").content(json.writeValueAsString(Map.of(
            "categoryId", category, "sku", "SNAP-" + suffix, "name", "购买时的设备",
            "description", "购买时的规格", "price", "19.90", "stock", 3))))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).path("data");
    long productId = created.path("id").asLong();
    jdbc.update("UPDATE product SET image_url=? WHERE id=?", "/images/products/MOUSE-001.webp", productId);
    MockHttpSession member = login("member1", "Member@12345");
    JsonNode cart = json.readTree(mvc.perform(put("/api/cart").session(member).with(csrf())
        .contentType("application/json").content(json.writeValueAsString(Map.of("productId", productId, "quantity", 1))))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).path("data");
    assertThat(cart.toString()).contains("购买时的规格", "/images/products/MOUSE-001.webp");
    Map<String, Object> items = Map.of("items", java.util.List.of(Map.of("productId", productId, "quantity", 1)));
    JsonNode preview = json.readTree(mvc.perform(post("/api/orders/preview").session(member).with(csrf())
        .contentType("application/json").content(json.writeValueAsString(items)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).path("data");
    assertThat(preview.path("items").get(0).path("description").asText()).isEqualTo("购买时的规格");
    JsonNode order = json.readTree(mvc.perform(post("/api/orders/checkout").session(admin).with(csrf())
        .contentType("application/json").content(json.writeValueAsString(Map.of(
            "requestKey", "image-" + suffix, "items", items.get("items")))))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).path("data");
    jdbc.update("UPDATE product SET sku=?,name=?,description=?,image_url=? WHERE id=?",
        "EDIT-" + suffix, "改后的设备", "改后的规格", "/images/products/KEY-001.webp", productId);
    JsonNode saved = getData("/api/orders/" + order.path("id").asLong(), admin).path("items").get(0);
    assertThat(saved.path("sku").asText()).isEqualTo("SNAP-" + suffix);
    assertThat(saved.path("productName").asText()).isEqualTo("购买时的设备");
    assertThat(saved.path("description").asText()).isEqualTo("购买时的规格");
    assertThat(saved.path("imageUrl").asText()).isEqualTo("/images/products/MOUSE-001.webp");
    assertThat(saved.path("unitPrice").decimalValue()).isEqualByComparingTo("19.90");
    mvc.perform(delete("/api/cart/" + productId).session(member).with(csrf())).andExpect(status().isOk());
  }

  private JsonNode findSku(JsonNode catalog, String sku) {
    for (JsonNode product : catalog) if (product.path("sku").asText().equals(sku)) return product;
    throw new AssertionError("缺少种子商品 " + sku);
  }

  private MockHttpSession login(String username, String password) throws Exception {
    return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
        .contentType("application/json").content(json.writeValueAsString(Map.of("username", username, "password", password))))
        .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
  }

  private JsonNode getData(String path, MockHttpSession session) throws Exception {
    return json.readTree(mvc.perform(get(path).session(session)).andExpect(status().isOk()).andReturn()
        .getResponse().getContentAsByteArray()).path("data");
  }
}
