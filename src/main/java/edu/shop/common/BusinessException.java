package edu.shop.common;

/** 预期内的业务失败，例如库存不足、权限不足；事务遇到此运行时异常会回滚。 */
public class BusinessException extends RuntimeException {
  private final int status;

  /** 默认的业务错误对应 HTTP 400。 */
  public BusinessException(String message) {
    this(400, message);
  }

  /** 可指定 HTTP 401、403、404、409 等状态。 */
  public BusinessException(int status, String message) {
    super(message);
    this.status = status;
  }

  /** 供统一异常处理器读取状态码。 */
  public int getStatus() {
    return status;
  }
}
