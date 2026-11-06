package edu.shop.queue;

import java.time.LocalDateTime;

/** 排队票据持久化对象。payloadJson 是入队时的购物车快照，不从页面接收会员身份。 */
public class CheckoutTicket {
  private Long id;
  private Long memberId;
  private String memberName;
  private Long cashierId;
  private String status;
  private String payloadJson;
  private String requestKey;
  private Long orderId;
  private String errorMessage;
  private Integer counterNo;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** 获取票据编号。 */
  public Long getId() {
    return id;
  }

  /** 设置数据库自动生成的票据编号。 */
  public void setId(Long id) {
    this.id = id;
  }

  /** 获取票据所属会员。 */
  public Long getMemberId() {
    return memberId;
  }

  /** 设置票据所属会员。 */
  public void setMemberId(Long memberId) {
    this.memberId = memberId;
  }

  /** 获取会员显示名称。 */
  public String getMemberName() {
    return memberName;
  }

  /** 设置会员显示名称。 */
  public void setMemberName(String memberName) {
    this.memberName = memberName;
  }

  /** 获取受理的销售员。 */
  public Long getCashierId() {
    return cashierId;
  }

  /** 设置受理的销售员。 */
  public void setCashierId(Long cashierId) {
    this.cashierId = cashierId;
  }

  /** 获取票据当前状态。 */
  public String getStatus() {
    return status;
  }

  /** 设置票据当前状态。 */
  public void setStatus(String status) {
    this.status = status;
  }

  /** 获取服务器生成的商品快照。 */
  public String getPayloadJson() {
    return payloadJson;
  }

  /** 设置服务器生成的商品快照。 */
  public void setPayloadJson(String payloadJson) {
    this.payloadJson = payloadJson;
  }

  /** 获取幂等请求编号。 */
  public String getRequestKey() {
    return requestKey;
  }

  /** 设置幂等请求编号。 */
  public void setRequestKey(String requestKey) {
    this.requestKey = requestKey;
  }

  /** 获取成功后生成的订单编号。 */
  public Long getOrderId() {
    return orderId;
  }

  /** 设置成功后生成的订单编号。 */
  public void setOrderId(Long orderId) {
    this.orderId = orderId;
  }

  /** 获取失败原因。 */
  public String getErrorMessage() {
    return errorMessage;
  }

  /** 设置失败原因。 */
  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  /** 获取柜台序号。 */
  public Integer getCounterNo() {
    return counterNo;
  }

  /** 设置柜台序号。 */
  public void setCounterNo(Integer counterNo) {
    this.counterNo = counterNo;
  }

  /** 获取创建时间。 */
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /** 设置创建时间。 */
  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  /** 获取更新时间。 */
  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  /** 设置更新时间。 */
  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
