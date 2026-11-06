package edu.shop.admin;

import edu.shop.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

/** 收银员注册和安排仅向店长开放；公开注册仍只创建会员账号。 */
@RestController
@RequestMapping("/api/cashiers")
public class CashierController {
  private final CashierService service;

  public CashierController(CashierService service) { this.service = service; }

  public record CounterRequest(@Min(1) @Max(4) int counterNo) {}
  public record EnabledRequest(@NotNull Boolean enabled) {}

  @GetMapping
  public ApiResponse<?> list() { return ApiResponse.ok(service.list()); }

  @PostMapping
  public ApiResponse<?> create(@Valid @RequestBody CashierService.CreateRequest body) {
    return ApiResponse.ok(service.create(body));
  }

  @PutMapping("/{id}/counter")
  public ApiResponse<?> assign(@PathVariable long id, @Valid @RequestBody CounterRequest body) {
    return ApiResponse.ok(service.assign(id, body.counterNo()));
  }

  @PatchMapping("/{id}/enabled")
  public ApiResponse<?> enabled(@PathVariable long id, @Valid @RequestBody EnabledRequest body) {
    service.enabled(id, body.enabled());
    return ApiResponse.ok();
  }
}
