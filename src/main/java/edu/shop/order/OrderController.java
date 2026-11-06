package edu.shop.order;

import static edu.shop.order.OrderRequests.*;

import edu.shop.common.ApiResponse;
import edu.shop.security.AuthContext;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/** 控制器只负责接收 HTTP/JSON、验证参数并调用业务服务，不把 SQL 写在页面或控制器里。 */
@RestController
@RequestMapping("/api")
public class OrderController {
  private final OrderService service;
  private final AuthContext auth;

  /** 构造方法注入订单业务对象。 */
  public OrderController(OrderService service, AuthContext auth) {
    this.service = service;
    this.auth = auth;
  }

  /** GET：查询自己的购物车。 */
  @GetMapping("/cart")
  public ApiResponse<List<CartItem>> cart() {
    return ok(service.cartFor(auth.require()));
  }

  /** PUT：设置商品数量；数量为 0 表示删除。 */
  @PutMapping("/cart")
  public ApiResponse<List<CartItem>> cart(@Valid @RequestBody CartRequest request) {
    return ok(service.updateCart(auth.require(), request));
  }

  /** DELETE：移除自己购物车中的一项。 */
  @DeleteMapping("/cart/{productId}")
  public ApiResponse<List<CartItem>> remove(@PathVariable long productId) {
    return ok(service.updateCart(auth.require(), new CartRequest(productId, 0)));
  }

  /** POST：后端试算优惠，页面不能自行决定应付金额。 */
  @PostMapping("/orders/preview")
  public ApiResponse<OrderPreview> preview(@Valid @RequestBody PreviewRequest request) {
    return ok(service.preview(auth.require(), request));
  }

  /** POST：员工模拟支付并提交整张订单事务。 */
  @PostMapping("/orders/checkout")
  public ApiResponse<SaleOrder> checkout(@Valid @RequestBody CheckoutRequest request) {
    return ok(service.checkoutFor(auth.require(), request));
  }

  /** GET：按登录角色查询可见订单。 */
  @GetMapping("/orders")
  public ApiResponse<List<SaleOrder>> orders() {
    return ok(service.listFor(auth.require()));
  }

  /** GET：老板查看全部退款申请，待审核优先，不受普通订单列表上限影响。 */
  @GetMapping("/refunds")
  public ApiResponse<List<SaleOrder>> refunds() {
    return ok(service.refundRequestsFor(auth.require()));
  }

  /** GET：查询一张有权查看的订单。 */
  @GetMapping("/orders/{id}")
  public ApiResponse<SaleOrder> order(@PathVariable long id) {
    return ok(service.getFor(id, auth.require()));
  }

  /** 旧的直接退款入口已关闭，防止旧页面绕过会员申请和老板审核。 */
  @PostMapping("/orders/{id}/refund")
  public ApiResponse<SaleOrder> directRefundDisabled(@PathVariable long id) {
    auth.require();
    throw new edu.shop.common.BusinessException(403, "请由会员提交退款申请，再由老板审核");
  }

  /** POST：会员申请自己订单的整单退款；不会立即退库存或积分。 */
  @PostMapping("/orders/{id}/refund-request")
  public ApiResponse<SaleOrder> requestRefund(
      @PathVariable long id, @Valid @RequestBody RefundRequest request) {
    return ok(service.requestRefund(auth.require(), id, request.reason()));
  }

  /** POST：仅老板可审核。批准才执行退款；拒绝必须填写原因。 */
  @PostMapping("/orders/{id}/refund-review")
  public ApiResponse<SaleOrder> reviewRefund(
      @PathVariable long id, @Valid @RequestBody RefundReviewRequest request) {
    return ok(service.reviewRefund(auth.require(), id, request.approve(), request.reason()));
  }

  /** GET：会员查询自己的积分流水。 */
  @GetMapping("/points")
  public ApiResponse<List<PointEntry>> points() {
    return ok(service.pointsFor(auth.require()));
  }

  /** 泛型方法：为不同返回数据统一包装 success/data/message。 */
  private <T> ApiResponse<T> ok(T value) {
    return new ApiResponse<>(true, value, "ok");
  }
}
