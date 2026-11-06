-- 新增实物图和购买时规格快照。Flyway 只执行一次；不修改 V1/V2 的校验和。
ALTER TABLE product ADD COLUMN image_url VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE order_item ADD COLUMN sku VARCHAR(40) NOT NULL DEFAULT '';
ALTER TABLE order_item ADD COLUMN description VARCHAR(500) NOT NULL DEFAULT '';
ALTER TABLE order_item ADD COLUMN image_url VARCHAR(255) NOT NULL DEFAULT '';

-- 历史订单没有保存过图片、货号和规格，因此新增字段保留空值。
-- 不把当前商品资料倒填到历史订单，避免旧牛奶订单错误显示为手机。
-- 仅明确匹配既有演示资料；老板自行更名的商品、库存和上下架状态均保留。
UPDATE product SET image_url='/images/products/PHONE-001.webp' WHERE sku='PHONE-001' AND name='iPhone 16';
UPDATE product SET image_url='/images/products/PHONE-002.webp' WHERE sku='PHONE-002' AND name='HUAWEI nova 13';
UPDATE product SET image_url='/images/products/PHONE-003.webp' WHERE sku='PHONE-003' AND name='Xiaomi 15';
UPDATE product SET image_url='/images/products/PHONE-004.webp' WHERE sku='PHONE-004' AND name='vivo X200';
UPDATE product SET image_url='/images/products/PC-001.webp' WHERE sku='PC-001' AND name='联想 ThinkBook 14';
UPDATE product SET image_url='/images/products/PC-002.webp' WHERE sku='PC-002' AND name IN ('MacBook Air 13','MacBook Air 13英寸');
UPDATE product SET image_url='/images/products/PC-003.webp' WHERE sku='PC-003' AND name IN ('华硕 天选 5','华硕 天选5 Pro');
UPDATE product SET image_url='/images/products/WATCH-001.webp' WHERE sku='WATCH-001' AND name IN ('HUAWEI WATCH GT 5','华为 WATCH GT 5');
UPDATE product SET image_url='/images/products/WATCH-002.webp' WHERE sku='WATCH-002' AND name='Apple Watch Series 10';
UPDATE product SET image_url='/images/products/GPU-001.webp' WHERE sku='GPU-001' AND name IN ('GeForce RTX 4060','微星 RTX 4060 VENTUS 2X');
UPDATE product SET image_url='/images/products/GPU-002.webp' WHERE sku='GPU-002' AND name IN ('GeForce RTX 4070 SUPER','技嘉 RTX 4070 SUPER GAMING OC');
UPDATE product SET image_url='/images/products/GPU-003.webp' WHERE sku='GPU-003' AND name IN ('Radeon RX 7800 XT','蓝宝石 RX 7800 XT NITRO+');

-- 只在名称和描述仍是旧默认值时纠正型号；不改老板自行填写的规格和售价。
UPDATE product SET description='锐龙 7 7730U · 16GB+512GB SSD · 14英寸 · 灰色'
WHERE sku='PC-001' AND name='联想 ThinkBook 14' AND description='锐龙7 · 16GB+512GB SSD · 14英寸 · 商务轻薄本';
UPDATE product SET name='MacBook Air 13英寸'
WHERE sku='PC-002' AND name='MacBook Air 13';
UPDATE product SET name='华硕 天选5 Pro',description='i7-13650HX · RTX 4060 · 16GB+1TB SSD · 16英寸 · 灰色'
WHERE sku='PC-003' AND name='华硕 天选 5' AND description='酷睿i7 · RTX 4060 · 16GB+1TB SSD · 15.6英寸游戏本';
UPDATE product SET name='华为 WATCH GT 5' WHERE sku='WATCH-001' AND name='HUAWEI WATCH GT 5';
UPDATE product SET description='42mm · GPS版 · 亮黑色铝金属表壳 · 黑色运动型表带'
WHERE sku='WATCH-002' AND name='Apple Watch Series 10' AND description='42mm · GPS版 · 铝金属表壳 · 运动型表带';
UPDATE product SET name='微星 RTX 4060 VENTUS 2X',description='8GB GDDR6 · 双风扇 · PCIe 4.0 · 黑色 OC 版'
WHERE sku='GPU-001' AND name='GeForce RTX 4060' AND description='8GB GDDR6 · PCIe 4.0 · 双风扇 · 独立显卡';
UPDATE product SET name='技嘉 RTX 4070 SUPER GAMING OC',description='12GB GDDR6X · 三风扇 · RGB 灯环 · GAMING OC'
WHERE sku='GPU-002' AND name='GeForce RTX 4070 SUPER' AND description='12GB GDDR6X · PCIe 4.0 · 三风扇 · 独立显卡';
UPDATE product SET name='蓝宝石 RX 7800 XT NITRO+',description='16GB GDDR6 · 三风扇 · ARGB 灯带 · NITRO+'
WHERE sku='GPU-003' AND name='Radeon RX 7800 XT' AND description='16GB GDDR6 · PCIe 4.0 · 三风扇 · 独立显卡';

-- 空库稍后由 Java 一次性创建 14 件种子，避免与初始化器重复。
-- 已有数码店则补第五类和两件新外设，不重置已有商品和订单。
INSERT INTO category(name)
SELECT '键鼠外设' WHERE EXISTS (SELECT 1 FROM product WHERE sku='PHONE-001')
AND NOT EXISTS (SELECT 1 FROM category WHERE name='键鼠外设');

INSERT INTO product(category_id,sku,name,description,price,stock,low_stock_threshold,on_sale,emoji,image_url)
SELECT c.id,'MOUSE-001','罗技 G304 无线鼠标','LIGHTSPEED 无线 · HERO 12K传感器 · 6个可编程按键 · 黑色',199.00,20,5,TRUE,'🖱️','/images/products/MOUSE-001.webp'
FROM category c WHERE c.name='键鼠外设' AND NOT EXISTS (SELECT 1 FROM product WHERE sku='MOUSE-001');
INSERT INTO product(category_id,sku,name,description,price,stock,low_stock_threshold,on_sale,emoji,image_url)
SELECT c.id,'KEY-001','罗技 K380s 蓝牙键盘','Pebble Keys 2 · 多设备蓝牙 · 圆形低噪按键 · 石墨灰',249.00,16,5,TRUE,'⌨️','/images/products/KEY-001.webp'
FROM category c WHERE c.name='键鼠外设' AND NOT EXISTS (SELECT 1 FROM product WHERE sku='KEY-001');

-- 为新增外设的初始数量保留可对账流水；已有正常建档商品已有流水，不会重复记账。
INSERT INTO inventory_log(product_id,change_qty,stock_after,type,reference_no,operator_id,remark)
SELECT p.id,p.stock,p.stock,'INITIAL','V3-CATALOG',NULL,'数码店新增键鼠的初始库存'
FROM product p WHERE ((p.sku='MOUSE-001' AND p.name='罗技 G304 无线鼠标' AND p.stock=20)
OR (p.sku='KEY-001' AND p.name='罗技 K380s 蓝牙键盘' AND p.stock=16))
AND NOT EXISTS (SELECT 1 FROM inventory_log l WHERE l.product_id=p.id);
