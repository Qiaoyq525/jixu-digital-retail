package edu.shop.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** 请求 DTO 集合：record 中的字段来自页面 JSON，数量和编号在入口先校验。 */
public final class OrderRequests {
  private OrderRequests() {}

  public record Item(@NotNull @Positive Long productId, @Min(1) @Max(999) int quantity) {}

  public record PreviewRequest(
      @Positive Long memberId, @NotEmpty @Size(max = 100) List<@Valid Item> items) {}

  public record CheckoutRequest(
      @Positive Long memberId,
      @NotEmpty @Size(max = 100) List<@Valid Item> items,
      @NotBlank @Size(max = 100) String requestKey) {}

  public record CartRequest(@NotNull @Positive Long productId, @Min(0) @Max(999) int quantity) {}

  public record RefundReviewRequest(@NotNull Boolean approve, @Size(max = 200) String reason) {}

  public record RefundRequest(@NotBlank @Size(max = 200) String reason) {}
}
