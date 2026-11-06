package edu.shop.catalog;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import edu.shop.admin.AuditMapper;
import edu.shop.admin.InventoryMapper;
import edu.shop.common.BusinessException;
import edu.shop.security.AuthContext;
import edu.shop.security.CurrentUser;
import edu.shop.user.Role;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 商品维护规则测试；SQL 是否修改 stock 由真实数据库集成测试补充保证。 */
@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {
  @Mock CatalogMapper mapper;
  @Mock AuthContext auth;
  @Mock AuditMapper audit;
  @Mock InventoryMapper inventory;
  private CatalogService service;
  private final CurrentUser admin = new CurrentUser(1L, "admin", "店长", Role.ADMIN);

  @BeforeEach
  void setUp() {
    service = new CatalogService(mapper, auth, audit, inventory);
  }

  @Test
  void creatingProductAppliesDefaultsAndNormalizesText() {
    asAdmin();
    when(mapper.categoryExists(3L)).thenReturn(1);
    assignInsertedId();
    service.create(request(null, null, null, null));
    ArgumentCaptor<Product> inserted = ArgumentCaptor.forClass(Product.class);
    verify(mapper).insert(inserted.capture());
    Product product = inserted.getValue();
    assertThat(product.getSku()).isEqualTo("SKU-01");
    assertThat(product.getName()).isEqualTo("测试商品");
    assertThat(product.getDescription()).isEmpty();
    assertThat(product.getStock()).isZero();
    assertThat(product.getLowStockThreshold()).isEqualTo(5);
    assertThat(product.isOnSale()).isTrue();
    assertThat(product.getEmoji()).isEqualTo("📦");
    assertThat(product.getImageUrl()).isEmpty();
    verifyNoInteractions(inventory);
  }

  @Test
  void initialStockHasMatchingInventoryRecord() {
    asAdmin();
    when(mapper.categoryExists(3L)).thenReturn(1);
    assignInsertedId();
    service.create(request(12, 2, false, "🧪"));
    verify(inventory).initial(8L, 12, 1L, "商品建档");
    verify(audit).add(1L, "新增商品", "SKU-01 测试商品");
  }

  @Test
  void invalidCategoryPreventsCreatingProductOrInventory() {
    asAdmin();
    assertThatThrownBy(() -> service.create(request(12, null, null, null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("有效分类");
    verify(mapper, never()).insert(any());
    verifyNoInteractions(inventory, audit);
  }

  @Test
  void editingProductDoesNotUseStockIncrementOrInitialStockWriter() {
    asAdmin();
    Product old = new Product();
    old.setImageUrl("/images/products/PHONE-001.webp");
    when(mapper.find(8L)).thenReturn(old);
    when(mapper.categoryExists(3L)).thenReturn(1);
    service.update(8L, request(999, 2, false, "🧪"));
    ArgumentCaptor<Product> edited = ArgumentCaptor.forClass(Product.class);
    verify(mapper).update(edited.capture());
    assertThat(edited.getValue().getId()).isEqualTo(8L);
    assertThat(edited.getValue().getImageUrl()).isEqualTo("/images/products/PHONE-001.webp");
    verify(mapper, never()).addStock(anyLong(), anyInt());
    verify(mapper, never()).insert(any());
    verifyNoInteractions(inventory);
    verify(audit).add(1L, "编辑商品", "测试商品");
  }

  @Test
  void editingMissingProductReports404WithoutWriting() {
    asAdmin();
    assertThatThrownBy(() -> service.update(8L, request(1, null, null, null)))
        .isInstanceOfSatisfying(
            BusinessException.class, exception -> assertThat(exception.getStatus()).isEqualTo(404));
    verify(mapper, never()).update(any());
    verifyNoInteractions(inventory, audit);
  }

  @Test
  void listingForMemberHidesOffSaleProductsAtQueryBoundary() {
    when(auth.require()).thenReturn(new CurrentUser(3L, "member1", "会员", Role.MEMBER));
    when(mapper.list("奶", 3L, true)).thenReturn(List.of());
    assertThat(service.list("奶", 3L)).isEmpty();
    verify(mapper).list("奶", 3L, true);
  }

  @Test
  void staffProductQueryCanIncludeOffSaleProducts() {
    when(auth.require()).thenReturn(admin);
    service.list(null, null);
    verify(mapper).list(null, null, false);
  }

  @Test
  void unauthenticatedOrForbiddenCallerCannotModifyProducts() {
    when(auth.requireRole(Role.ADMIN)).thenThrow(new BusinessException(403, "没有权限"));
    assertThatThrownBy(() -> service.create(request(1, null, null, null)))
        .isInstanceOf(BusinessException.class);
    verifyNoInteractions(mapper, inventory, audit);
  }

  @Test
  void saleChangesAreAuditedAndMissingProductIsReported() {
    asAdmin();
    when(mapper.sale(8L, false)).thenReturn(1);
    when(mapper.sale(8L, true)).thenReturn(1);
    service.sale(8L, false);
    service.sale(8L, true);
    verify(audit).add(1L, "下架商品", "商品编号 8");
    verify(audit).add(1L, "上架商品", "商品编号 8");
    assertThatThrownBy(() -> service.sale(99L, false))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不存在");
  }

  @Test
  void deleteReportsMissingProductAndAuditsSuccessfulDelete() {
    asAdmin();
    when(mapper.delete(8L)).thenReturn(1);
    service.delete(8L);
    verify(audit).add(1L, "删除商品", "商品编号 8");
    assertThatThrownBy(() -> service.delete(99L))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不存在");
  }

  @Test
  void categoryNameMustBePresentAndAtMost60Characters() {
    asAdmin();
    assertThatThrownBy(() -> service.category(null, null, false))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> service.category(null, "  ", false))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> service.category(null, "类".repeat(61), false))
        .isInstanceOf(BusinessException.class);
    verifyNoInteractions(mapper, audit);
  }

  @Test
  void categoryCreateAndEditTrimNameAndReportMissingRows() {
    asAdmin();
    service.category(null, " 饮品 ", false);
    verify(mapper).addCategory("饮品");
    when(mapper.updateCategory(3L, "饮品茶咖")).thenReturn(1);
    service.category(3L, " 饮品茶咖 ", false);
    verify(mapper).updateCategory(3L, "饮品茶咖");
    assertThatThrownBy(() -> service.category(99L, "饮品", false))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不存在");
  }

  @Test
  void categoryDeleteDoesNotRequireNameAndReportsMissingRows() {
    asAdmin();
    when(mapper.deleteCategory(3L)).thenReturn(1);
    service.category(3L, null, true);
    verify(audit).add(1L, "维护商品分类", "删除分类 3");
    assertThatThrownBy(() -> service.category(99L, null, true))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不存在");
  }

  private void asAdmin() {
    when(auth.requireRole(Role.ADMIN)).thenReturn(admin);
  }

  private void assignInsertedId() {
    doAnswer(
            invocation -> {
              invocation.<Product>getArgument(0).setId(8L);
              return 1;
            })
        .when(mapper)
        .insert(any());
  }

  private CatalogService.ProductRequest request(
      Integer stock, Integer threshold, Boolean onSale, String emoji) {
    return new CatalogService.ProductRequest(
        3L, " SKU-01 ", " 测试商品 ", null, new BigDecimal("12.50"), stock, threshold, onSale, emoji);
  }
}
