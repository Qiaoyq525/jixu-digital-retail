package edu.shop.order;

/** 数据库查询映射对象：通过 getter/setter 读写，不包含密码字段。 */
public class SaleOrder {
  private Long id;
  private String orderNo;
  private Long memberId;
  private String memberName;
  private Long cashierId;
  private String cashierName;
  private String status;
  private java.math.BigDecimal originalAmount;
  private java.math.BigDecimal productDiscount;
  private java.math.BigDecimal memberDiscount;
  private java.math.BigDecimal fullReduction;
  private java.math.BigDecimal payableAmount;
  private Integer earnedPoints;
  private String discountDescription;
  private String requestKey;
  private java.time.LocalDateTime createdAt;
  private java.time.LocalDateTime paidAt;
  private java.time.LocalDateTime refundedAt;
  private java.util.List<OrderItem> items;
  private String refundStatus;
  private String refundReason;
  private String refundReviewNote;
  private java.time.LocalDateTime refundRequestedAt;
  private java.time.LocalDateTime refundReviewedAt;


  /** 获取 id 字段。 */
  public Long getId() {
    return id;
  }

  /** 设置 id 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setId(Long id) {
    this.id = id;
  }

  /** 获取 orderNo 字段。 */
  public String getOrderNo() {
    return orderNo;
  }

  /** 设置 orderNo 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setOrderNo(String orderNo) {
    this.orderNo = orderNo;
  }

  /** 获取 memberId 字段。 */
  public Long getMemberId() {
    return memberId;
  }

  /** 设置 memberId 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setMemberId(Long memberId) {
    this.memberId = memberId;
  }

  /** 获取 memberName 字段。 */
  public String getMemberName() {
    return memberName;
  }

  /** 设置 memberName 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setMemberName(String memberName) {
    this.memberName = memberName;
  }

  /** 获取 cashierId 字段。 */
  public Long getCashierId() {
    return cashierId;
  }

  /** 设置 cashierId 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setCashierId(Long cashierId) {
    this.cashierId = cashierId;
  }

  /** 获取 cashierName 字段。 */
  public String getCashierName() {
    return cashierName;
  }

  /** 设置 cashierName 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setCashierName(String cashierName) {
    this.cashierName = cashierName;
  }

  /** 获取 status 字段。 */
  public String getStatus() {
    return status;
  }

  /** 设置 status 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setStatus(String status) {
    this.status = status;
  }

  /** 获取 originalAmount 字段。 */
  public java.math.BigDecimal getOriginalAmount() {
    return originalAmount;
  }

  /** 设置 originalAmount 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setOriginalAmount(java.math.BigDecimal originalAmount) {
    this.originalAmount = originalAmount;
  }

  /** 获取 productDiscount 字段。 */
  public java.math.BigDecimal getProductDiscount() {
    return productDiscount;
  }

  /** 设置 productDiscount 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setProductDiscount(java.math.BigDecimal productDiscount) {
    this.productDiscount = productDiscount;
  }

  /** 获取 memberDiscount 字段。 */
  public java.math.BigDecimal getMemberDiscount() {
    return memberDiscount;
  }

  /** 设置 memberDiscount 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setMemberDiscount(java.math.BigDecimal memberDiscount) {
    this.memberDiscount = memberDiscount;
  }

  /** 获取 fullReduction 字段。 */
  public java.math.BigDecimal getFullReduction() {
    return fullReduction;
  }

  /** 设置 fullReduction 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setFullReduction(java.math.BigDecimal fullReduction) {
    this.fullReduction = fullReduction;
  }

  /** 获取 payableAmount 字段。 */
  public java.math.BigDecimal getPayableAmount() {
    return payableAmount;
  }

  /** 设置 payableAmount 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setPayableAmount(java.math.BigDecimal payableAmount) {
    this.payableAmount = payableAmount;
  }

  /** 获取 earnedPoints 字段。 */
  public Integer getEarnedPoints() {
    return earnedPoints;
  }

  /** 设置 earnedPoints 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setEarnedPoints(Integer earnedPoints) {
    this.earnedPoints = earnedPoints;
  }

  /** 获取 discountDescription 字段。 */
  public String getDiscountDescription() {
    return discountDescription;
  }

  /** 设置 discountDescription 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setDiscountDescription(String discountDescription) {
    this.discountDescription = discountDescription;
  }

  /** 获取 requestKey 字段。 */
  public String getRequestKey() {
    return requestKey;
  }

  /** 设置 requestKey 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setRequestKey(String requestKey) {
    this.requestKey = requestKey;
  }

  /** 获取 createdAt 字段。 */
  public java.time.LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /** 设置 createdAt 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setCreatedAt(java.time.LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  /** 获取 paidAt 字段。 */
  public java.time.LocalDateTime getPaidAt() {
    return paidAt;
  }

  /** 设置 paidAt 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setPaidAt(java.time.LocalDateTime paidAt) {
    this.paidAt = paidAt;
  }

  /** 获取 refundedAt 字段。 */
  public java.time.LocalDateTime getRefundedAt() {
    return refundedAt;
  }

  /** 设置 refundedAt 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setRefundedAt(java.time.LocalDateTime refundedAt) {
    this.refundedAt = refundedAt;
  }

  /** 获取 items 字段。 */
  public java.util.List<OrderItem> getItems() {
    return items;
  }

  /** 设置 items 字段，供 MyBatis 映射或业务方法组装结果。 */
  public void setItems(java.util.List<OrderItem> items) {
    this.items = items;
  }

  public String getRefundStatus() { return refundStatus; }
  public void setRefundStatus(String value) { refundStatus = value; }

  public String getRefundReason() { return refundReason; }
  public void setRefundReason(String value) { refundReason = value; }

  public String getRefundReviewNote() { return refundReviewNote; }
  public void setRefundReviewNote(String value) { refundReviewNote = value; }

  public java.time.LocalDateTime getRefundRequestedAt() { return refundRequestedAt; }
  public void setRefundRequestedAt(java.time.LocalDateTime value) { refundRequestedAt = value; }

  public java.time.LocalDateTime getRefundReviewedAt() { return refundReviewedAt; }
  public void setRefundReviewedAt(java.time.LocalDateTime value) { refundReviewedAt = value; }

  /** 含改版前种子商品的整单保留历史快照，不能退回当前数码商品的库存。 */
  public boolean isLegacyDemoOrder() {
    return items != null && items.stream().anyMatch(OrderItem::isLegacyDemoItem);
  }
}
