package edu.shop.queue;

import java.util.List;
import org.apache.ibatis.annotations.*;

/** 排队数据库接口。所有状态变化使用条件更新，取消和柜台认领不会同时成功。 */
@Mapper
public interface QueueMapper {
  /** 兼容旧版本的中断窗口：已有付款订单的票据先恢复为完成，绝不能提示会员再次支付。 */
  @Update(
      """
      UPDATE checkout_ticket SET status='COMPLETED',
        order_id=(SELECT o.id FROM shop_order o WHERE o.request_key=CONCAT('queue:',checkout_ticket.id)),
        error_message=NULL,updated_at=CURRENT_TIMESTAMP
      WHERE status IN ('WAITING','PROCESSING','FAILED')
        AND EXISTS(SELECT 1 FROM shop_order o WHERE o.request_key=CONCAT('queue:',checkout_ticket.id))
      """)
  int recoverPaid();

  /** 保留已分配的等待票据；只清理旧自动流程留下的中断或未分配票据。 */
  @Update(
      "UPDATE checkout_ticket SET"
          + " status='FAILED',error_message='旧结账流程已中断，请重新提交到收银台',updated_at=CURRENT_TIMESTAMP WHERE"
          + " status='PROCESSING' OR (status='WAITING' AND (counter_no IS NULL OR cashier_id IS NULL))")
  int recoverInterrupted();

  /** 同会员已有未完成票据时不能重复排队。 */
  @Select(
      "SELECT COUNT(*) FROM checkout_ticket WHERE member_id=#{memberId} AND status IN"
          + " ('WAITING','PROCESSING')")
  int activeCount(long memberId);

  /** 幂等编号用于网络重试时返回原票据。 */
  @Select("SELECT * FROM checkout_ticket WHERE request_key=#{key}")
  CheckoutTicket byKey(String key);

  /** 查询一张票据的归属及状态。 */
  @Select("SELECT * FROM checkout_ticket WHERE id=#{id}")
  CheckoutTicket ticket(long id);

  /** 确认前读取最新票据并加锁；不使用事务开始前的旧状态付款。 */
  @Select("SELECT * FROM checkout_ticket WHERE id=#{id} FOR UPDATE")
  CheckoutTicket lockTicket(long id);

  /** 所有分配按柜台编号顺序加锁；跨请求也能安全选择最短队列。 */
  @Select("SELECT counter_no,cashier_id FROM checkout_counter ORDER BY counter_no FOR UPDATE")
  List<CheckoutCounter> lockCounters();

  /** 同一柜台一次只允许一个确认事务，不同柜台仍可并发结账。 */
  @Select("SELECT counter_no,cashier_id FROM checkout_counter WHERE counter_no=#{counterNo} FOR UPDATE")
  CheckoutCounter lockCounter(int counterNo);

  /** 启动时仅填充空柜台，不覆盖已经固定的员工映射。 */
  @Update("UPDATE checkout_counter SET cashier_id=#{cashierId} WHERE counter_no=#{counterNo} AND cashier_id IS NULL")
  int assignCounter(@Param("counterNo") int counterNo, @Param("cashierId") long cashierId);

  /** 柜台看板包含员工名称和真实数据库待办数量。 */
  @Select("""
      SELECT c.counter_no,c.cashier_id,u.display_name cashier_name,u.username cashier_username,
        u.enabled cashier_enabled,u.role cashier_role,
        (SELECT COUNT(*) FROM checkout_ticket t WHERE t.counter_no=c.counter_no AND t.status='WAITING') waiting_count,
        (SELECT COUNT(*) FROM checkout_ticket t WHERE t.counter_no=c.counter_no AND t.status='PROCESSING') processing_count,
        (SELECT MIN(t.id) FROM checkout_ticket t WHERE t.counter_no=c.counter_no AND t.status IN ('WAITING','PROCESSING')) ticket_id
      FROM checkout_counter c LEFT JOIN shop_user u ON u.id=c.cashier_id ORDER BY c.counter_no
      """)
  List<CheckoutCounter> counters();

  /** 柜台内按提交顺序处理；位置为 1 的票据才可确认。 */
  @Select("SELECT COUNT(*) FROM checkout_ticket WHERE counter_no=#{counterNo} AND status IN ('WAITING','PROCESSING') AND id<=#{id}")
  int position(@Param("counterNo") int counterNo, @Param("id") long id);

  /** 保存排队票据，主键自动回填。 */
  @Insert(
      "INSERT INTO checkout_ticket(member_id,cashier_id,counter_no,status,payload_json,request_key,created_at,updated_at)"
          + " VALUES(#{memberId},#{cashierId},#{counterNo},'WAITING',#{payloadJson},#{requestKey},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(CheckoutTicket ticket);

  /** 柜台认领，仅 WAITING 可以转为 PROCESSING。 */
  @Update(
      "UPDATE checkout_ticket SET"
          + " status='PROCESSING',cashier_id=#{cashierId},counter_no=#{counterNo},updated_at=CURRENT_TIMESTAMP"
          + " WHERE id=#{id} AND status='WAITING' AND cashier_id=#{cashierId} AND counter_no=#{counterNo}")
  int claim(
      @Param("id") long id, @Param("cashierId") long cashierId, @Param("counterNo") int counterNo);

  /** 支付成功后记录对应订单。 */
  @Update(
      "UPDATE checkout_ticket SET"
          + " status='COMPLETED',order_id=#{orderId},updated_at=CURRENT_TIMESTAMP WHERE id=#{id}"
          + " AND status='PROCESSING'")
  int complete(@Param("id") long id, @Param("orderId") long orderId);

  /** 会员只能取消自己尚未认领的票据。 */
  @Update(
      "UPDATE checkout_ticket SET status='CANCELLED',updated_at=CURRENT_TIMESTAMP WHERE id=#{id}"
          + " AND member_id=#{memberId} AND status='WAITING'")
  int cancel(@Param("id") long id, @Param("memberId") long memberId);

  /** 会员只看自己，收银员只看分配给自己的票据；活动票据优先，最多 300 条。 */
  @Select(
      """
      <script>SELECT t.*,u.display_name member_name FROM checkout_ticket t JOIN shop_user u ON u.id=t.member_id
      <where><if test="memberId != null">t.member_id=#{memberId}</if>
        <if test="cashierId != null">AND t.cashier_id=#{cashierId}</if></where>
      ORDER BY CASE WHEN t.status IN ('WAITING','PROCESSING') THEN 0 ELSE 1 END,t.id DESC LIMIT 300</script>
      """)
  List<CheckoutTicket> list(@Param("memberId") Long memberId, @Param("cashierId") Long cashierId);
}
