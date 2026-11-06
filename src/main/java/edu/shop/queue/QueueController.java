package edu.shop.queue;

import edu.shop.common.ApiResponse;
import edu.shop.security.AuthContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** 排队 HTTP 入口；权限与身份在服务端验证。 */
@RestController
@RequestMapping("/api/queue")
public class QueueController {
  private final QueueService queue;
  private final AuthContext auth;

  public record JoinRequest(@NotBlank @Size(max = 100) String requestKey) {}

  /** 构造方法注入排队协调器与当前用户身份。 */
  public QueueController(QueueService queue, AuthContext auth) {
    this.queue = queue;
    this.auth = auth;
  }

  /** GET：读取柜台和自己有权查看的排队票据。 */
  @GetMapping
  public ApiResponse<Map<String, Object>> snapshot() {
    return ok(queue.snapshot(auth.require()));
  }

  /** POST：把登录会员的购物车快照加入结账队列。 */
  @PostMapping("/join")
  public ApiResponse<Map<String, Object>> join(@Valid @RequestBody JoinRequest request) {
    return ok(queue.join(auth.require(), request.requestKey()));
  }

  /** POST：取消自己的待处理票据。 */
  @PostMapping("/{id}/cancel")
  public ApiResponse<Map<String, Object>> cancel(@PathVariable long id) {
    return ok(queue.cancel(auth.require(), id));
  }

  /** POST：分配到的收银员在工作台主动确认，才执行库存和订单事务。 */
  @PostMapping("/{id}/confirm")
  public ApiResponse<Map<String, Object>> confirm(@PathVariable long id) {
    return ok(queue.confirm(auth.require(), id));
  }

  /** 泛型包装方法：统一 JSON 外层结构。 */
  private <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, "ok");
  }
}
