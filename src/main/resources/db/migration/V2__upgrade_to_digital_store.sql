-- 将旧版演示资料升级为淇曜数码；Flyway 只执行一次，不清库、不重设库存。
-- 商品主键不变，历史订单明细的名称/单价快照、库存流水、购物车和票据均保留。
-- 只匹配明确的旧演示SKU与名称，用户改名的商品不会被覆盖。重复执行也不会重复修改。
UPDATE shop_user SET display_name='乔胤淇' WHERE username='admin' AND role='ADMIN';
UPDATE category SET name='手机通讯' WHERE name='饮品茶咖' AND NOT EXISTS (SELECT 1 FROM (SELECT name FROM category GROUP BY name) existing_category WHERE existing_category.name='手机通讯');
UPDATE category SET name='电脑整机' WHERE name='烘焙零食' AND NOT EXISTS (SELECT 1 FROM (SELECT name FROM category GROUP BY name) existing_category WHERE existing_category.name='电脑整机');
UPDATE category SET name='智能穿戴' WHERE name='生活好物' AND NOT EXISTS (SELECT 1 FROM (SELECT name FROM category GROUP BY name) existing_category WHERE existing_category.name='智能穿戴');
UPDATE category SET name='显卡硬件' WHERE name='文具手账' AND NOT EXISTS (SELECT 1 FROM (SELECT name FROM category GROUP BY name) existing_category WHERE existing_category.name='显卡硬件');

UPDATE product SET sku='PHONE-001',name='iPhone 16',description='128GB · A18芯片 · 6.1英寸 OLED · 黑色',price=5499.00,emoji='📱' WHERE sku='DRINK-001' AND name='牧场鲜牛奶' AND NOT EXISTS (SELECT 1 FROM (SELECT sku FROM product GROUP BY sku) existing_product WHERE existing_product.sku='PHONE-001');
UPDATE product SET sku='PHONE-002',name='HUAWEI nova 13',description='12GB+256GB · 6.7英寸 OLED · 100W快充 · 羽砂紫',price=2699.00,emoji='📱' WHERE sku='DRINK-002' AND name='冷萃拿铁' AND NOT EXISTS (SELECT 1 FROM (SELECT sku FROM product GROUP BY sku) existing_product WHERE existing_product.sku='PHONE-002');
UPDATE product SET sku='PHONE-003',name='Xiaomi 15',description='12GB+256GB · 骁龙8至尊版 · 6.36英寸 · 浅草绿',price=4299.00,emoji='📱' WHERE sku='DRINK-003' AND name='白桃乌龙茶' AND NOT EXISTS (SELECT 1 FROM (SELECT sku FROM product GROUP BY sku) existing_product WHERE existing_product.sku='PHONE-003');
UPDATE product SET sku='PHONE-004',name='vivo X200',description='12GB+256GB · 天玑9400 · 6.67英寸 · 极光绿',price=3999.00,emoji='📱' WHERE sku='DRINK-004' AND name='气泡柠檬水' AND NOT EXISTS (SELECT 1 FROM (SELECT sku FROM product GROUP BY sku) existing_product WHERE existing_product.sku='PHONE-004');
UPDATE product SET sku='PC-001',name='联想 ThinkBook 14',description='锐龙7 · 16GB+512GB SSD · 14英寸 · 商务轻薄本',price=4599.00,emoji='💻' WHERE sku='SNACK-001' AND name='黄油可颂' AND NOT EXISTS (SELECT 1 FROM (SELECT sku FROM product GROUP BY sku) existing_product WHERE existing_product.sku='PC-001');
UPDATE product SET sku='PC-002',name='MacBook Air 13',description='M4芯片 · 16GB+256GB SSD · 13.6英寸 · 午夜色',price=7499.00,emoji='💻' WHERE sku='SNACK-002' AND name='黑巧曲奇' AND NOT EXISTS (SELECT 1 FROM (SELECT sku FROM product GROUP BY sku) existing_product WHERE existing_product.sku='PC-002');
UPDATE product SET sku='PC-003',name='华硕 天选 5',description='酷睿i7 · RTX 4060 · 16GB+1TB SSD · 15.6英寸游戏本',price=6999.00,emoji='💻' WHERE sku='SNACK-003' AND name='每日坚果' AND NOT EXISTS (SELECT 1 FROM (SELECT sku FROM product GROUP BY sku) existing_product WHERE existing_product.sku='PC-003');
UPDATE product SET sku='WATCH-001',name='HUAWEI WATCH GT 5',description='46mm · 心率与睡眠监测 · 双频定位 · 黑色氟橡胶表带',price=1488.00,emoji='⌚' WHERE sku='LIFE-001' AND name='帆布小提袋' AND NOT EXISTS (SELECT 1 FROM (SELECT sku FROM product GROUP BY sku) existing_product WHERE existing_product.sku='WATCH-001');
UPDATE product SET sku='WATCH-002',name='Apple Watch Series 10',description='42mm · GPS版 · 铝金属表壳 · 运动型表带',price=2599.00,emoji='⌚' WHERE sku='LIFE-002' AND name='陶瓷随手杯' AND NOT EXISTS (SELECT 1 FROM (SELECT sku FROM product GROUP BY sku) existing_product WHERE existing_product.sku='WATCH-002');
UPDATE product SET sku='GPU-001',name='GeForce RTX 4060',description='8GB GDDR6 · PCIe 4.0 · 双风扇 · 独立显卡',price=2199.00,emoji='🖥️' WHERE sku='NOTE-001' AND name='方格手账本' AND NOT EXISTS (SELECT 1 FROM (SELECT sku FROM product GROUP BY sku) existing_product WHERE existing_product.sku='GPU-001');
UPDATE product SET sku='GPU-002',name='GeForce RTX 4070 SUPER',description='12GB GDDR6X · PCIe 4.0 · 三风扇 · 独立显卡',price=4599.00,emoji='🖥️' WHERE sku='NOTE-002' AND name='极简中性笔' AND NOT EXISTS (SELECT 1 FROM (SELECT sku FROM product GROUP BY sku) existing_product WHERE existing_product.sku='GPU-002');
UPDATE product SET sku='GPU-003',name='Radeon RX 7800 XT',description='16GB GDDR6 · PCIe 4.0 · 三风扇 · 独立显卡',price=3699.00,emoji='🖥️' WHERE sku='NOTE-003' AND name='彩色便利贴' AND NOT EXISTS (SELECT 1 FROM (SELECT sku FROM product GROUP BY sku) existing_product WHERE existing_product.sku='GPU-003');

UPDATE supplier SET name='华讯数码供应链' WHERE name='晨光食品供应站' AND NOT EXISTS (SELECT 1 FROM (SELECT name FROM supplier GROUP BY name) existing_supplier WHERE existing_supplier.name='华讯数码供应链');
UPDATE supplier SET name='星联电脑配件商贸' WHERE name='拾物生活配送' AND NOT EXISTS (SELECT 1 FROM (SELECT name FROM supplier GROUP BY name) existing_supplier WHERE existing_supplier.name='星联电脑配件商贸');

-- 仅当两个值仍是旧默认值时，成组升级；老板自行设置的优惠不覆盖。
UPDATE shop_setting SET setting_value=CASE setting_key WHEN 'FULL_REDUCTION_THRESHOLD' THEN '3000.00' ELSE '100.00' END
WHERE setting_key IN ('FULL_REDUCTION_THRESHOLD','FULL_REDUCTION_AMOUNT')
AND (SELECT COUNT(*) FROM (SELECT setting_key,setting_value FROM shop_setting GROUP BY setting_key,setting_value) old_defaults
     WHERE (old_defaults.setting_key='FULL_REDUCTION_THRESHOLD' AND old_defaults.setting_value='100.00')
        OR (old_defaults.setting_key='FULL_REDUCTION_AMOUNT' AND old_defaults.setting_value='10.00'))=2;