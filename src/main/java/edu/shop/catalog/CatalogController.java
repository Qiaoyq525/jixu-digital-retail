package edu.shop.catalog;

import edu.shop.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

/** 商品和分类HTTP入口；请求JSON由Spring转换成方法参数。 */
@RestController
@RequestMapping("/api")
public class CatalogController {
  private final CatalogService service;

  /** 注入商品业务。 */
  public CatalogController(CatalogService service) {
    this.service = service;
  }

  public record NameRequest(@NotBlank String name) {}

  public record SaleRequest(boolean onSale) {}

  /** GET 查询列表。 */
  @GetMapping("/products")
  public ApiResponse<?> products(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Long categoryId) {
    return ApiResponse.ok(service.list(keyword, categoryId));
  }

  /** POST 创建商品。 */
  @PostMapping("/products")
  public ApiResponse<?> create(@Valid @RequestBody CatalogService.ProductRequest body) {
    return ApiResponse.ok(service.create(body));
  }

  /** PUT 编辑商品。 */
  @PutMapping("/products/{id}")
  public ApiResponse<?> update(
      @PathVariable long id, @Valid @RequestBody CatalogService.ProductRequest body) {
    return ApiResponse.ok(service.update(id, body));
  }

  /** PATCH 上下架。 */
  @PatchMapping("/products/{id}/sale")
  public ApiResponse<?> sale(@PathVariable long id, @RequestBody SaleRequest body) {
    service.sale(id, body.onSale());
    return ApiResponse.ok();
  }

  /** DELETE 删除商品。 */
  @DeleteMapping("/products/{id}")
  public ApiResponse<?> delete(@PathVariable long id) {
    service.delete(id);
    return ApiResponse.ok();
  }

  /** 查询分类。 */
  @GetMapping("/categories")
  public ApiResponse<?> categories() {
    return ApiResponse.ok(service.categories());
  }

  /** 新增分类。 */
  @PostMapping("/categories")
  public ApiResponse<?> category(@Valid @RequestBody NameRequest body) {
    service.category(null, body.name(), false);
    return ApiResponse.ok();
  }

  /** 修改分类。 */
  @PutMapping("/categories/{id}")
  public ApiResponse<?> category(@PathVariable long id, @Valid @RequestBody NameRequest body) {
    service.category(id, body.name(), false);
    return ApiResponse.ok();
  }

  /** 删除分类。 */
  @DeleteMapping("/categories/{id}")
  public ApiResponse<?> category(@PathVariable long id) {
    service.category(id, null, true);
    return ApiResponse.ok();
  }
}
