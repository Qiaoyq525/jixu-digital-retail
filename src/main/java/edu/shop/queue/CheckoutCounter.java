package edu.shop.queue;

/** 固定收银台映射；员工停用时保留映射，避免会员看到柜台忽然换人。 */
public class CheckoutCounter {
  private Integer counterNo;
  private Long cashierId;
  private String cashierName;
  private String cashierUsername;
  private Boolean cashierEnabled;
  private String cashierRole;
  private Integer waitingCount;
  private Integer processingCount;
  private Long ticketId;

  public Integer getCounterNo() { return counterNo; }
  public void setCounterNo(Integer value) { counterNo = value; }
  public Long getCashierId() { return cashierId; }
  public void setCashierId(Long value) { cashierId = value; }
  public String getCashierName() { return cashierName; }
  public void setCashierName(String value) { cashierName = value; }
  public String getCashierUsername() { return cashierUsername; }
  public void setCashierUsername(String value) { cashierUsername = value; }
  public Boolean getCashierEnabled() { return cashierEnabled; }
  public void setCashierEnabled(Boolean value) { cashierEnabled = value; }
  public String getCashierRole() { return cashierRole; }
  public void setCashierRole(String value) { cashierRole = value; }
  public Integer getWaitingCount() { return waitingCount; }
  public void setWaitingCount(Integer value) { waitingCount = value; }
  public Integer getProcessingCount() { return processingCount; }
  public void setProcessingCount(Integer value) { processingCount = value; }
  public Long getTicketId() { return ticketId; }
  public void setTicketId(Long value) { ticketId = value; }
}
