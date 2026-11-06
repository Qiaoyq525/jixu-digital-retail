package edu.shop.admin;

import edu.shop.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 后台HTTP入口，权限判断位于AdminService，避免只在页面隐藏按钮。 */
@RestController
@RequestMapping("/api")
public class AdminController {
  private final AdminService service;

  /** 注入后台服务。 */
  public AdminController(AdminService service) {
    this.service = service;
  }

  public record EnabledRequest(boolean enabled) {}

  /** 供应商列表。 */
  @GetMapping("/suppliers")
  public ApiResponse<?> suppliers() {
    return ApiResponse.ok(service.suppliers());
  }

  /** 新增供应商。 */
  @PostMapping("/suppliers")
  public ApiResponse<?> supplier(@Valid @RequestBody AdminService.SupplierRequest body) {
    service.supplier(null, body);
    return ApiResponse.ok();
  }

  /** 编辑供应商。 */
  @PutMapping("/suppliers/{id}")
  public ApiResponse<?> supplier(
      @PathVariable long id, @Valid @RequestBody AdminService.SupplierRequest body) {
    service.supplier(id, body);
    return ApiResponse.ok();
  }

  /** 删除供应商。 */
  @DeleteMapping("/suppliers/{id}")
  public ApiResponse<?> deleteSupplier(@PathVariable long id) {
    service.deleteSupplier(id);
    return ApiResponse.ok();
  }

  /** 采购列表。 */
  @GetMapping("/purchases")
  public ApiResponse<?> purchases() {
    return ApiResponse.ok(service.purchases());
  }

  /** 提交采购并入库。 */
  @PostMapping("/purchases")
  public ApiResponse<?> purchase(@Valid @RequestBody AdminService.PurchaseRequest body) {
    return ApiResponse.ok(service.purchase(body));
  }

  /** 库存流水。 */
  @GetMapping("/inventory")
  public ApiResponse<?> inventory() {
    return ApiResponse.ok(service.inventory());
  }

  /** 公开用户资料。 */
  @GetMapping("/users")
  public ApiResponse<?> users() {
    return ApiResponse.ok(service.users());
  }

  /** 启停账号。 */
  @PatchMapping("/users/{id}/enabled")
  public ApiResponse<?> enabled(@PathVariable long id, @RequestBody EnabledRequest body) {
    service.enabled(id, body.enabled());
    return ApiResponse.ok();
  }

  /** 获取优惠和柜台设置。 */
  @GetMapping("/settings")
  public ApiResponse<?> settings() {
    return ApiResponse.ok(service.settings());
  }

  /** 修改优惠和柜台设置。 */
  @PutMapping("/settings")
  public ApiResponse<?> settings(@Valid @RequestBody AdminService.SettingsRequest body) {
    service.settings(body);
    return ApiResponse.ok();
  }

  /** 老板业务统计。 */
  @GetMapping("/stats")
  public ApiResponse<?> stats() {
    return ApiResponse.ok(service.stats());
  }

  /** 操作日志。 */
  @GetMapping("/audit")
  public ApiResponse<?> audit() {
    return ApiResponse.ok(service.audit());
  }

  /** 浏览器下载CSV。 */
  @GetMapping("/export/orders.csv")
  public ResponseEntity<byte[]> csv() {
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=shop-orders.csv")
        .body(service.exportOrders());
  }
}
