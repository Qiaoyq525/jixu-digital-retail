package edu.shop.catalog;

import edu.shop.admin.AuditMapper;
import edu.shop.common.BusinessException;
import edu.shop.security.AuthContext;
import edu.shop.user.Role;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 商品业务层负责权限、字段检查和事务；Controller只负责接收HTTP。 */
@Service
public class CatalogService {
  private final CatalogMapper mapper;
  private final AuthContext auth;
  private final AuditMapper audit;
  private final edu.shop.admin.InventoryMapper inventory;

  /** 构造方法注入协作对象。 */
  public CatalogService(
      CatalogMapper mapper,
      AuthContext auth,
      AuditMapper audit,
      edu.shop.admin.InventoryMapper inventory) {
    this.mapper = mapper;
    this.auth = auth;
    this.audit = audit;
    this.inventory = inventory;
  }

  public record ProductRequest(
      @NotNull Long categoryId,
      @NotBlank @Size(max = 40) String sku,
      @NotBlank @Size(max = 100) String name,
      @Size(max = 500) String description,
      @NotNull @DecimalMin("0.01") @DecimalMax("9999999.99") @Digits(integer = 7, fraction = 2)
          BigDecimal price,
      @Min(0) @Max(1000000) Integer stock,
      @Min(0) @Max(1000000) Integer lowStockThreshold,
      Boolean onSale,
      @Size(max = 16) String emoji) {}

  /** 返回当前角色允许看到的商品。 */
  public List<Product> list(String keyword, Long categoryId) {
    var user = auth.require();
    return mapper.list(keyword, categoryId, user.role() == Role.MEMBER);
  }

  /** 分类为登录用户共同可见。 */
  public List<Map<String, Object>> categories() {
    auth.require();
    return mapper.categories();
  }

  /** 老板添加商品及初始入库记录，任何一步出错全部回滚。 */
  @Transactional
  public Product create(ProductRequest body) {
    var user = auth.requireRole(Role.ADMIN);
    Product p = from(body);
    mapper.insert(p);
    if (p.getStock() > 0) initialStock(p, user.id());
    audit.add(user.id(), "新增商品", p.getSku() + " " + p.getName());
    return mapper.find(p.getId());
  }

  /** 修改资料不改变库存；已有库存只能通过采购、付款、退款变化。 */
  @Transactional
  public Product update(long id, ProductRequest body) {
    var user = auth.requireRole(Role.ADMIN);
    Product old = mapper.find(id);
    if (old == null) throw new BusinessException(404, "商品不存在");
    Product p = from(body);
    p.setId(id);
    p.setImageUrl(old.getImageUrl());
    mapper.update(p);
    audit.add(user.id(), "编辑商品", p.getName());
    return mapper.find(id);
  }

  /** 上下架是独立操作，下架后会员不可购买。 */
  @Transactional
  public void sale(long id, boolean onSale) {
    var user = auth.requireRole(Role.ADMIN);
    if (mapper.sale(id, onSale) == 0) throw new BusinessException(404, "商品不存在");
    audit.add(user.id(), onSale ? "上架商品" : "下架商品", "商品编号 " + id);
  }

  /** 删除仅允许未被订单等引用的商品。 */
  @Transactional
  public void delete(long id) {
    var user = auth.requireRole(Role.ADMIN);
    if (mapper.delete(id) == 0) throw new BusinessException(404, "商品不存在");
    audit.add(user.id(), "删除商品", "商品编号 " + id);
  }

  /** 老板维护分类。 */
  @Transactional
  public void category(Long id, String name, boolean delete) {
    var user = auth.requireRole(Role.ADMIN);
    if (delete) {
      if (mapper.deleteCategory(id) == 0) throw new BusinessException(404, "分类不存在");
    } else {
      if (name == null || name.isBlank() || name.trim().length() > 60)
        throw new BusinessException("分类名称为1至60个字符");
      if (id == null) mapper.addCategory(name.trim());
      else if (mapper.updateCategory(id, name.trim()) == 0)
        throw new BusinessException(404, "分类不存在");
    }
    audit.add(user.id(), "维护商品分类", delete ? "删除分类 " + id : name.trim());
  }

  /** 将请求转换为普通商品对象，给予可选字段默认值。 */
  private Product from(ProductRequest body) {
    if (mapper.categoryExists(body.categoryId()) == 0) throw new BusinessException("请选择有效分类");
    if (body.sku().trim().toUpperCase(java.util.Locale.ROOT).startsWith("DEMO-"))
      throw new BusinessException("DEMO- 是并发实验商品保留前缀，请换一个商品编码");
    Product p = new Product();
    p.setCategoryId(body.categoryId());
    p.setSku(body.sku().trim());
    p.setName(body.name().trim());
    p.setDescription(body.description() == null ? "" : body.description().trim());
    p.setPrice(body.price());
    p.setStock(body.stock() == null ? 0 : body.stock());
    p.setLowStockThreshold(body.lowStockThreshold() == null ? 5 : body.lowStockThreshold());
    p.setOnSale(body.onSale() == null || body.onSale());
    p.setImageUrl("");
    p.setEmoji(body.emoji() == null || body.emoji().isBlank() ? "📦" : body.emoji());
    return p;
  }

  private final String initialMarker = "商品建档";

  /** 初始库存也写流水，后续可以对账。 */
  private void initialStock(Product product, long userId) {
    inventory.initial(product.getId(), product.getStock(), userId, initialMarker);
  }
}
