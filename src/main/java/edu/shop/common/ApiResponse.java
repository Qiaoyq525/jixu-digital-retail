package edu.shop.common;

/** 泛型 T 表示本次响应的数据类型；record 会自动生成构造方法和只读访问方法。 */
public record ApiResponse<T>(boolean success, T data, String message) {
  /** 将任意业务结果包成统一 JSON，前端只需检查 success 和 data。 */
  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, "ok");
  }

  /** 操作没有返回数据时使用。 */
  public static ApiResponse<Void> ok() {
    return ok(null);
  }

  /** 只返回可读原因，不泄露堆栈、SQL 或密码。 */
  public static ApiResponse<Void> fail(String message) {
    return new ApiResponse<>(false, null, message);
  }
}
