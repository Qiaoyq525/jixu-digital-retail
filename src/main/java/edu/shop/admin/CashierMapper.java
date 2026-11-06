package edu.shop.admin;

import java.util.List;
import org.apache.ibatis.annotations.*;

/** 收银员名额和柜台安排；写操作统一先锁四个柜台，再读取最新状态。 */
@Mapper
public interface CashierMapper {
  /** 显式选择公开字段，密码哈希不进入管理列表。 */
  @Select("""
      SELECT u.id,u.username,u.display_name,u.enabled,c.counter_no
      FROM shop_user u LEFT JOIN checkout_counter c ON c.cashier_id=u.id
      WHERE u.role='SALES' ORDER BY u.id
      """)
  List<CashierService.CashierView> list();

  /** 当前读包含停用账号，数据库锁保证并发注册也不会超过四人。 */
  @Select("SELECT id FROM shop_user WHERE role='SALES' ORDER BY id FOR UPDATE")
  List<Long> lockCashierIds();

  /** 当前读检查待办，避免 MySQL 可重复读中的旧快照漏掉刚提交的队列。 */
  @Select("""
      SELECT id FROM checkout_ticket
      WHERE status IN ('WAITING','PROCESSING')
        AND (cashier_id=#{cashierId} OR counter_no=#{source} OR counter_no=#{target})
      ORDER BY id FOR UPDATE
      """)
  List<Long> lockActiveTickets(
      @Param("cashierId") long cashierId, @Param("source") Integer source,
      @Param("target") Integer target);

  /** 改派先解除原映射；事务中已持有全部柜台锁，其他请求看不到中间状态。 */
  @Update("UPDATE checkout_counter SET cashier_id=NULL WHERE cashier_id=#{cashierId}")
  int clearCounter(long cashierId);

  /** 新安排的柜台立即开放；不会缩小现有营业范围。 */
  @Update("""
      UPDATE shop_setting SET setting_value=#{counterNo}
      WHERE setting_key='COUNTER_COUNT' AND CAST(setting_value AS DECIMAL(4,0))<#{counterNo}
      """)
  int openThrough(int counterNo);
}
