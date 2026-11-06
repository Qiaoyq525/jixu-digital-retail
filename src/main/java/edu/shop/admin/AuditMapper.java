package edu.shop.admin;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 操作审计只记录动作及业务编号，不记录密码或会话token。 */
@Mapper
public interface AuditMapper {
  /** 在业务事务内记录操作，失败时一起回滚。 */
  @Insert(
      "INSERT INTO audit_log(operator_id,action,detail) VALUES(#{operatorId},#{action},#{detail})")
  int add(
      @Param("operatorId") Long operatorId,
      @Param("action") String action,
      @Param("detail") String detail);

  /** 查询最近500条日志。 */
  @Select(
      "SELECT a.id,u.display_name AS operatorName,a.action,a.detail,a.created_at AS createdAt FROM"
          + " audit_log a LEFT JOIN shop_user u ON u.id=a.operator_id ORDER BY a.id DESC LIMIT 500")
  List<Map<String, Object>> list();
}
