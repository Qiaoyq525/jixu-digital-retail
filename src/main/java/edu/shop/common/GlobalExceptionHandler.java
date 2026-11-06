package edu.shop.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 将异常统一翻译成前端能展示的错误；数据库出错时不会返回原始 SQL。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** 处理主动抛出的业务异常。 */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> business(BusinessException ex) {
    return ResponseEntity.status(ex.getStatus()).body(ApiResponse.fail(ex.getMessage()));
  }

  /** 校验请求字段，例如名称为空、价格为负。 */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(e -> e.getField() + "：" + e.getDefaultMessage())
            .orElse("请求参数不正确");
    return ResponseEntity.badRequest().body(ApiResponse.fail(message));
  }

  /** JSON 格式或枚举值不对时，给出统一提示。 */
  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    IllegalArgumentException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class
  })
  public ResponseEntity<ApiResponse<Void>> malformed(Exception ex) {
    return ResponseEntity.badRequest().body(ApiResponse.fail("请求格式或参数不正确"));
  }

  /** 唯一键、外键等约束保护数据后，将失败转换成可读的冲突提示。 */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> conflict(DataIntegrityViolationException ex) {
    return ResponseEntity.status(409).body(ApiResponse.fail("数据重复或已被其他业务引用，请检查后重试"));
  }

  /** 权限检查异常对应403。 */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> denied(AccessDeniedException ex) {
    return ResponseEntity.status(403).body(ApiResponse.fail("没有权限执行此操作"));
  }

  /** 路径存在但 HTTP 方法用错时，保留正确的 405 状态。 */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> method(HttpRequestMethodNotSupportedException ex) {
    return ResponseEntity.status(405).body(ApiResponse.fail("该接口不支持此请求方法"));
  }

  /** 未知错误写入后端日志，页面只显示通用信息。 */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> unexpected(Exception ex) {
    LOG.error("请求处理失败", ex);
    return ResponseEntity.internalServerError().body(ApiResponse.fail("处理失败，请稍后重试或查看运行日志"));
  }
}
