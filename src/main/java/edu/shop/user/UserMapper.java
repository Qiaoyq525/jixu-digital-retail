package edu.shop.user;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** MyBatis 根据接口上的 SQL 生成实现对象；#{...} 使用预编译参数。 */
@Mapper
public interface UserMapper {
  /** 根据用户名查询认证信息，只供后端使用。 */
  @Select("SELECT * FROM shop_user WHERE username=#{username}")
  ShopUser findByUsername(String username);

  /** 根据主键查询用户最新状态。 */
  @Select("SELECT * FROM shop_user WHERE id=#{id}")
  ShopUser findById(long id);

  /** 新建用户，数据库自增编号写回 id。 */
  @Insert(
      "INSERT INTO shop_user(username,password_hash,display_name,role,points,total_spent,enabled)"
          + " VALUES(#{username},#{passwordHash},#{displayName},#{role},#{points},#{totalSpent},#{enabled})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(ShopUser user);

  /** 管理员列表显式选择公开字段，避免把密码带出数据库。 */
  @Select(
      "SELECT id,username,display_name AS displayName,role,points,total_spent AS"
          + " totalSpent,enabled,created_at AS createdAt FROM shop_user WHERE"
          + " LOWER(SUBSTRING(username,1,5)) != 'demo_' ORDER BY id")
  List<Map<String, Object>> listPublic();

  /** 修改启用状态。 */
  @Update("UPDATE shop_user SET enabled=#{enabled} WHERE id=#{id}")
  int setEnabled(@Param("id") long id, @Param("enabled") boolean enabled);

  /** 在用户行锁内检查等待单，避免停用后会员无法取消、导致柜台队列堵塞。 */
  @Select("SELECT COUNT(*) FROM checkout_ticket WHERE member_id=#{id} AND status IN ('WAITING','PROCESSING')")
  int activeMemberTickets(long id);

  /** 判断是否为空库，种子数据仅在空库创建。 */
  @Select("SELECT COUNT(*) FROM shop_user")
  int count();
}
