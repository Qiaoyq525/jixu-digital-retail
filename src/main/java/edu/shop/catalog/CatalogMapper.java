package edu.shop.catalog;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 商品与分类SQL。动态条件只拼SQL结构，用户输入全部通过#{...}绑定。 */
@Mapper
public interface CatalogMapper {
  /** 按角色、关键词和分类筛选商品。 */
  @Select(
      """
      <script>SELECT p.*,c.name AS category_name FROM product p JOIN category c ON p.category_id=c.id
      WHERE p.sku NOT LIKE 'DEMO-%' <if test="member">AND p.on_sale=TRUE</if>
      <if test="keyword != null and keyword != ''">AND (p.name LIKE CONCAT('%',#{keyword},'%') OR p.sku LIKE CONCAT('%',#{keyword},'%') OR p.description LIKE CONCAT('%',#{keyword},'%'))</if>
      <if test="categoryId != null">AND p.category_id=#{categoryId}</if> ORDER BY p.id</script>
      """)
  List<Product> list(
      @Param("keyword") String keyword,
      @Param("categoryId") Long categoryId,
      @Param("member") boolean member);

  /** 查询单个商品。 */
  @Select(
      "SELECT p.*,c.name AS category_name FROM product p JOIN category c ON p.category_id=c.id"
          + " WHERE p.id=#{id}")
  Product find(long id);

  /** 采购时加行锁，使采购与支付/退款串行更新同一商品。 */
  @Select("SELECT * FROM product WHERE id=#{id} FOR UPDATE")
  Product lock(long id);

  /** 新建商品并回填主键。 */
  @Insert(
      "INSERT INTO"
          + " product(category_id,sku,name,description,price,stock,low_stock_threshold,on_sale,emoji,image_url)"
          + " VALUES(#{categoryId},#{sku},#{name},#{description},#{price},#{stock},#{lowStockThreshold},#{onSale},#{emoji},#{imageUrl})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(Product product);

  /** 编辑商品资料时故意不更新stock，防止绕过库存流水。 */
  @Update(
      "UPDATE product SET"
          + " category_id=#{categoryId},sku=#{sku},name=#{name},description=#{description},price=#{price},low_stock_threshold=#{lowStockThreshold},on_sale=#{onSale},emoji=#{emoji},image_url=#{imageUrl}"
          + " WHERE id=#{id}")
  int update(Product product);

  /** 上下架商品。 */
  @Update("UPDATE product SET on_sale=#{onSale} WHERE id=#{id}")
  int sale(@Param("id") long id, @Param("onSale") boolean onSale);

  /** 删除无业务引用商品，有引用时由外键拒绝。 */
  @Delete("DELETE FROM product WHERE id=#{id}")
  int delete(long id);

  /** 采购入库增加数量，之前必须已经锁行。 */
  @Update("UPDATE product SET stock=stock+#{quantity} WHERE id=#{id}")
  int addStock(@Param("id") long id, @Param("quantity") int quantity);

  /** 所有分类，供下拉框和导航使用。 */
  @Select("SELECT id,name FROM category ORDER BY id")
  List<Map<String, Object>> categories();

  /** 判断分类是否存在。 */
  @Select("SELECT COUNT(*) FROM category WHERE id=#{id}")
  int categoryExists(long id);

  /** 新建分类。 */
  @Insert("INSERT INTO category(name) VALUES(#{name})")
  int addCategory(String name);

  /** 修改分类。 */
  @Update("UPDATE category SET name=#{name} WHERE id=#{id}")
  int updateCategory(@Param("id") long id, @Param("name") String name);

  /** 删除分类，已有商品引用则失败。 */
  @Delete("DELETE FROM category WHERE id=#{id}")
  int deleteCategory(long id);
}
