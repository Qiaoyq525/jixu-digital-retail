package edu.shop.admin;

import java.math.BigDecimal;

/** 采购主表对象；明细在同一事务内逐条保存。 */
public class PurchaseOrder {
  private Long id;
  private Long supplierId;
  private Long operatorId;
  private BigDecimal totalAmount;

  /** 读取采购编号。 */
  public Long getId() {
    return id;
  }

  /** 保存数据库生成编号。 */
  public void setId(Long id) {
    this.id = id;
  }

  /** 读取供应商。 */
  public Long getSupplierId() {
    return supplierId;
  }

  /** 设置供应商。 */
  public void setSupplierId(Long id) {
    supplierId = id;
  }

  /** 读取操作人。 */
  public Long getOperatorId() {
    return operatorId;
  }

  /** 设置操作人。 */
  public void setOperatorId(Long id) {
    operatorId = id;
  }

  /** 读取采购金额。 */
  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  /** 设置采购金额。 */
  public void setTotalAmount(BigDecimal value) {
    totalAmount = value;
  }
}
