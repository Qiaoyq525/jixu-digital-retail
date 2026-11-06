package edu.shop.admin;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import edu.shop.catalog.CatalogMapper;
import edu.shop.catalog.Product;
import edu.shop.common.BusinessException;
import edu.shop.security.AuthContext;
import edu.shop.security.CurrentUser;
import edu.shop.user.Role;
import edu.shop.user.UserMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 管理业务的边界测试；真正的提交、回滚和数据库约束由集成测试验证。 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {
  @Mock AdminMapper mapper;
  @Mock CatalogMapper products;
  @Mock UserMapper users;
  @Mock AuditMapper audit;
  @Mock InventoryMapper inventory;
  @Mock AuthContext auth;
  @Mock CashierService cashiers;

  private final CurrentUser admin = new CurrentUser(1L, "admin", "店长", Role.ADMIN);
  private AdminService service;

  @BeforeEach
  void setUp() {
    service = new AdminService(mapper, products, users, audit, inventory, auth, cashiers);
  }

  @Test
  void missingSupplierStopsBeforeLockingOrWritingProducts() {
    asAdmin();
    assertThatThrownBy(() -> service.purchase(purchase(line(5L, 2, "8"))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("供应商");
    verifyNoInteractions(products, inventory, audit);
    verify(mapper, never()).insertPurchase(any());
  }

  @Test
  void duplicateProductsMustBeCombinedBeforePurchase() {
    asAdmin();
    when(mapper.supplierExists(3L)).thenReturn(1);
    assertThatThrownBy(() -> service.purchase(purchase(line(5L, 2, "8"), line(5L, 3, "9"))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("合并");
    verifyNoInteractions(products, inventory, audit);
    verify(mapper, never()).insertPurchase(any());
  }

  @Test
  void nonexistentProductDoesNotCreatePurchaseOrInventoryLog() {
    asAdmin();
    when(mapper.supplierExists(3L)).thenReturn(1);
    assertThatThrownBy(() -> service.purchase(purchase(line(5L, 2, "8"))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("商品不存在");
    verify(mapper, never()).insertPurchase(any());
    verifyNoInteractions(inventory, audit);
  }

  @Test
  void stockLimitUsesLongArithmeticAndRejectsBeforeAnyWrite() {
    asAdmin();
    when(mapper.supplierExists(3L)).thenReturn(1);
    when(products.lock(5L)).thenReturn(product(5L, 999_999));
    assertThatThrownBy(() -> service.purchase(purchase(line(5L, 2, "8"))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("100万");
    verify(mapper, never()).insertPurchase(any());
    verify(products, never()).addStock(anyLong(), anyInt());
    verifyNoInteractions(inventory, audit);
  }

  @Test
  void amountBeyondDatabaseCapacityIsRejectedBeforeWriting() {
    asAdmin();
    when(mapper.supplierExists(3L)).thenReturn(1);
    when(products.lock(5L)).thenReturn(product(5L, 0));
    assertThatThrownBy(() -> service.purchase(purchase(line(5L, 100_000, "999999.99"))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("金额超出");
    verify(mapper, never()).insertPurchase(any());
    verifyNoInteractions(inventory, audit);
  }

  @Test
  void successfulPurchaseLocksInOrderAndRecordsExactCostsAndStock() {
    asAdmin();
    when(mapper.supplierExists(3L)).thenReturn(1);
    when(products.lock(2L)).thenReturn(product(2L, 10));
    when(products.lock(5L)).thenReturn(product(5L, 20));
    doAnswer(
            invocation -> {
              invocation.<PurchaseOrder>getArgument(0).setId(41L);
              return 1;
            })
        .when(mapper)
        .insertPurchase(any());

    Map<String, Object> result =
        service.purchase(purchase(line(5L, 3, "2.50"), line(2L, 2, "1.20")));

    assertThat(result.get("id")).isEqualTo(41L);
    assertThat((BigDecimal) result.get("totalAmount")).isEqualByComparingTo("9.90");
    var lockOrder = inOrder(products, mapper);
    lockOrder.verify(products).lock(2L);
    lockOrder.verify(products).lock(5L);
    lockOrder.verify(mapper).insertPurchase(any());
    ArgumentCaptor<PurchaseOrder> order = ArgumentCaptor.forClass(PurchaseOrder.class);
    verify(mapper).insertPurchase(order.capture());
    assertThat(order.getValue().getSupplierId()).isEqualTo(3L);
    assertThat(order.getValue().getOperatorId()).isEqualTo(1L);
    verify(mapper).purchaseItem(41L, 2L, 2, new BigDecimal("1.20"), new BigDecimal("2.40"));
    verify(mapper).purchaseItem(41L, 5L, 3, new BigDecimal("2.50"), new BigDecimal("7.50"));
    verify(inventory).purchase(2L, 2, 12, "PO41", 1L);
    verify(inventory).purchase(5L, 3, 23, "PO41", 1L);
    verify(products).addStock(2L, 2);
    verify(products).addStock(5L, 3);
  }

  @Test
  void forbiddenCallerCannotStartPurchaseDatabaseWork() {
    when(auth.requireRole(Role.ADMIN)).thenThrow(new BusinessException(403, "没有权限"));
    assertThatThrownBy(() -> service.purchase(purchase(line(5L, 2, "8"))))
        .isInstanceOf(BusinessException.class);
    verifyNoInteractions(mapper, products, inventory, audit);
  }

  @Test
  void supplierCreationTrimsTextAndDefaultsOptionalFields() {
    asAdmin();
    service.supplier(null, new AdminService.SupplierRequest("  供应商  ", null, null));
    verify(mapper).addSupplier("供应商", "", "");
    verify(audit).add(1L, "维护供应商", "供应商");
  }

  @Test
  void supplierUpdateAndDeleteReportMissingRows() {
    asAdmin();
    assertThatThrownBy(
            () -> service.supplier(8L, new AdminService.SupplierRequest("供应商", "张经理", "123")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不存在");
    assertThatThrownBy(() -> service.deleteSupplier(8L))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不存在");
    verifyNoInteractions(audit);
  }

  @Test
  void successfulSupplierUpdateAndDeleteAreAudited() {
    asAdmin();
    when(mapper.updateSupplier(8L, "供应商", "张经理", "123")).thenReturn(1);
    when(mapper.deleteSupplier(8L)).thenReturn(1);
    service.supplier(8L, new AdminService.SupplierRequest(" 供应商 ", " 张经理 ", " 123 "));
    service.deleteSupplier(8L);
    verify(audit).add(1L, "维护供应商", "供应商");
    verify(audit).add(1L, "删除供应商", "供应商编号 8");
  }

  @Test
  void bossCannotDisableSelfAndMissingUserIsReported() {
    asAdmin();
    assertThatThrownBy(() -> service.enabled(1L, false))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("当前登录");
    verify(users, never()).setEnabled(1L, false);
    assertThatThrownBy(() -> service.enabled(8L, false))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不存在");
    verifyNoInteractions(audit);
  }

  @Test
  void togglingExistingUserWritesAudit() {
    asAdmin();
    when(users.setEnabled(8L, false)).thenReturn(1);
    service.enabled(8L, false);
    verify(audit).add(1L, "停用用户", "用户编号 8");
    when(users.setEnabled(8L, true)).thenReturn(1);
    service.enabled(8L, true);
    verify(audit).add(1L, "启用用户", "用户编号 8");
  }

  @Test
  void fullReductionCannotExceedItsThreshold() {
    asAdmin();
    assertThatThrownBy(
            () ->
                service.settings(
                    new AdminService.SettingsRequest(
                        BigDecimal.ONE, new BigDecimal("10"), new BigDecimal("11"), 2)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不能大于");
    verifyNoInteractions(mapper, audit);
  }

  @Test
  void allSettingsArePersistedAndAuditedTogether() {
    asAdmin();
    service.settings(
        new AdminService.SettingsRequest(
            new BigDecimal("0.90"), new BigDecimal("100.00"), new BigDecimal("10.00"), 3));
    verify(mapper).updateSetting("PRODUCT_DISCOUNT_RATE", "0.90");
    verify(mapper).updateSetting("FULL_REDUCTION_THRESHOLD", "100.00");
    verify(mapper).updateSetting("FULL_REDUCTION_AMOUNT", "10.00");
    verify(mapper).updateSetting("COUNTER_COUNT", "3");
    verify(audit).add(eq(1L), eq("修改优惠与柜台"), contains("柜台 3"));
  }

  @Test
  void readingSettingsProducesTypedAmountsAndCounterCount() {
    when(mapper.settings())
        .thenReturn(
            List.of(
                Map.of("settingKey", "PRODUCT_DISCOUNT_RATE", "settingValue", "0.95"),
                Map.of("settingKey", "FULL_REDUCTION_THRESHOLD", "settingValue", "100"),
                Map.of("settingKey", "FULL_REDUCTION_AMOUNT", "settingValue", "10"),
                Map.of("settingKey", "COUNTER_COUNT", "settingValue", "4")));
    Map<String, Object> result = service.settings();
    verify(auth).require();
    assertThat((BigDecimal) result.get("productDiscountRate")).isEqualByComparingTo("0.95");
    assertThat(result.get("counterCount")).isEqualTo(4);
  }

  @ParameterizedTest
  @ValueSource(strings = {"=SUM(A1:A2)", "+1+2", "-1+2", "@SUM(A1:A2)", "\t=1", "\r=1", "\n=1"})
  void csvNeutralizesFormulaPrefixes(String text) {
    assertThat(AdminService.csvCell(text)).isEqualTo("\"'" + text + "\"");
  }

  @Test
  void csvQuotesCommasNewlinesAndEmbeddedQuotes() {
    assertThat(AdminService.csvCell("甲,\"乙\"\n丙")).isEqualTo("\"甲,\"\"乙\"\"\n丙\"");
    assertThat(AdminService.csvCell(null)).isEqualTo("\"\"");
    assertThat(AdminService.csvCell(new BigDecimal("12.50"))).isEqualTo("\"12.50\"");
  }

  @Test
  void exportedCsvHasUtf8BomAndOnlyExpectedColumns() {
    asAdmin();
    when(mapper.exportOrders())
        .thenReturn(
            List.of(
                Map.of(
                    "orderNo",
                    "SD001",
                    "status",
                    "PAID",
                    "memberName",
                    "=1+1",
                    "cashierName",
                    "小周",
                    "originalAmount",
                    new BigDecimal("20"),
                    "payableAmount",
                    new BigDecimal("18"),
                    "createdAt",
                    "2026-11-01")));
    byte[] result = service.exportOrders();
    assertThat(result).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
    assertThat(new String(result, StandardCharsets.UTF_8))
        .contains("订单号", "\"'=1+1\"", "小周")
        .doesNotContain("password", "requestKey");
  }

  @Test
  void salesCanSelectOnlyMemberPublicProfiles() {
    when(auth.requireRole(Role.ADMIN, Role.SALES))
        .thenReturn(new CurrentUser(2L, "cashier1", "销售", Role.SALES));
    Map<String, Object> member = Map.of("id", 3L, "role", "MEMBER");
    when(users.listPublic())
        .thenReturn(
            List.of(member, Map.of("id", 1L, "role", "ADMIN"), Map.of("id", 2L, "role", "SALES")));
    assertThat(service.users()).containsExactly(member);
  }

  @Test
  void purchaseListIncludesItsDetailsWithoutChangingMapperRows() {
    asAdmin();
    Map<String, Object> row = Map.of("id", 9L, "totalAmount", new BigDecimal("10"));
    Map<String, Object> item = Map.of("productId", 5L, "quantity", 2);
    when(mapper.purchases()).thenReturn(List.of(row));
    when(mapper.purchaseItems(9L)).thenReturn(List.of(item));
    assertThat(service.purchases().getFirst()).containsEntry("items", List.of(item));
    assertThat(row).doesNotContainKey("items");
  }

  @Test
  void statisticsFiltersDemoAndOnlyReportsLowStocks() {
    asAdmin();
    Product low = product(5L, 2);
    low.setSku("NORMAL-1");
    low.setLowStockThreshold(2);
    Product plenty = product(6L, 20);
    plenty.setSku("NORMAL-2");
    plenty.setLowStockThreshold(5);
    Product demo = product(7L, 1);
    demo.setSku("DEMO-run");
    demo.setLowStockThreshold(5);
    when(mapper.totals())
        .thenReturn(Map.of("todaySales", BigDecimal.ZERO, "totalSales", BigDecimal.TEN));
    when(products.list(null, null, false)).thenReturn(List.of(low, plenty, demo));
    Map<String, Object> result = service.stats();
    assertThat(result.get("lowStock")).isEqualTo(List.of(low));
    assertThat(result)
        .containsKeys("todaySales", "totalSales")
        .containsKeys("dailySales", "hotProducts", "cashierPerformance");
  }

  private void asAdmin() {
    when(auth.requireRole(Role.ADMIN)).thenReturn(admin);
  }

  private AdminService.PurchaseRequest purchase(AdminService.PurchaseLine... lines) {
    return new AdminService.PurchaseRequest(3L, List.of(lines));
  }

  private AdminService.PurchaseLine line(long productId, int quantity, String cost) {
    return new AdminService.PurchaseLine(productId, quantity, new BigDecimal(cost));
  }

  private Product product(long id, int stock) {
    Product product = new Product();
    product.setId(id);
    product.setStock(stock);
    return product;
  }
}
