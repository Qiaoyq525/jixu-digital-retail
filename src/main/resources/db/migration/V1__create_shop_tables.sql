-- Flyway 只执行一次的建表脚本；不删除已有表，后续升级新增 V2、V3 脚本。
CREATE TABLE shop_user (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(40) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, display_name VARCHAR(60) NOT NULL,
 role VARCHAR(16) NOT NULL, points INT NOT NULL DEFAULT 0,
 total_spent DECIMAL(12,2) NOT NULL DEFAULT 0, enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT ck_user_role CHECK (role IN ('ADMIN','SALES','MEMBER')),
 CONSTRAINT ck_user_points CHECK (points >= 0), CONSTRAINT ck_user_spent CHECK (total_spent >= 0)
);
CREATE TABLE category (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(60) NOT NULL UNIQUE, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE product (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, category_id BIGINT NOT NULL, sku VARCHAR(40) NOT NULL UNIQUE,
 name VARCHAR(100) NOT NULL, description VARCHAR(500) NOT NULL DEFAULT '', price DECIMAL(12,2) NOT NULL,
 stock INT NOT NULL DEFAULT 0, low_stock_threshold INT NOT NULL DEFAULT 5, on_sale BOOLEAN NOT NULL DEFAULT TRUE,
 emoji VARCHAR(16) NOT NULL DEFAULT '📦', created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(category_id) REFERENCES category(id), CONSTRAINT ck_product_price CHECK(price > 0),
 CONSTRAINT ck_product_stock CHECK(stock >= 0), CONSTRAINT ck_product_threshold CHECK(low_stock_threshold >= 0)
);
CREATE TABLE supplier (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE, contact VARCHAR(60) NOT NULL DEFAULT '', phone VARCHAR(40) NOT NULL DEFAULT '', created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE purchase_order (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, supplier_id BIGINT NOT NULL, operator_id BIGINT NOT NULL,
 total_amount DECIMAL(12,2) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(supplier_id) REFERENCES supplier(id), FOREIGN KEY(operator_id) REFERENCES shop_user(id)
);
CREATE TABLE purchase_item (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, purchase_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
 quantity INT NOT NULL, unit_cost DECIMAL(12,2) NOT NULL, subtotal DECIMAL(12,2) NOT NULL,
 FOREIGN KEY(purchase_id) REFERENCES purchase_order(id), FOREIGN KEY(product_id) REFERENCES product(id),
 CONSTRAINT ck_purchase_qty CHECK(quantity > 0), CONSTRAINT ck_purchase_cost CHECK(unit_cost >= 0)
);
CREATE TABLE cart_item (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, member_id BIGINT NOT NULL, product_id BIGINT NOT NULL, quantity INT NOT NULL,
 UNIQUE(member_id,product_id), FOREIGN KEY(member_id) REFERENCES shop_user(id), FOREIGN KEY(product_id) REFERENCES product(id), CONSTRAINT ck_cart_qty CHECK(quantity > 0)
);
CREATE TABLE shop_order (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(64) NOT NULL UNIQUE, member_id BIGINT, cashier_id BIGINT,
 status VARCHAR(24) NOT NULL, original_amount DECIMAL(12,2) NOT NULL,
 product_discount DECIMAL(12,2) NOT NULL DEFAULT 0, member_discount DECIMAL(12,2) NOT NULL DEFAULT 0,
 full_reduction DECIMAL(12,2) NOT NULL DEFAULT 0, payable_amount DECIMAL(12,2) NOT NULL,
 earned_points INT NOT NULL DEFAULT 0, discount_description VARCHAR(500) NOT NULL DEFAULT '',
 request_key VARCHAR(100) NOT NULL UNIQUE, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 paid_at TIMESTAMP NULL, refunded_at TIMESTAMP NULL,
 FOREIGN KEY(member_id) REFERENCES shop_user(id), FOREIGN KEY(cashier_id) REFERENCES shop_user(id),
 CONSTRAINT ck_order_status CHECK(status IN ('PAID','REFUNDED')), CONSTRAINT ck_order_pay CHECK(payable_amount >= 0)
);
CREATE TABLE order_item (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
 product_name VARCHAR(100) NOT NULL, unit_price DECIMAL(12,2) NOT NULL, quantity INT NOT NULL, subtotal DECIMAL(12,2) NOT NULL,
 FOREIGN KEY(order_id) REFERENCES shop_order(id), FOREIGN KEY(product_id) REFERENCES product(id), CONSTRAINT ck_order_item_qty CHECK(quantity > 0)
);
CREATE TABLE inventory_log (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, product_id BIGINT NOT NULL, change_qty INT NOT NULL, stock_after INT NOT NULL,
 type VARCHAR(30) NOT NULL, reference_no VARCHAR(64) NOT NULL, operator_id BIGINT, remark VARCHAR(500) NOT NULL DEFAULT '',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(product_id) REFERENCES product(id), FOREIGN KEY(operator_id) REFERENCES shop_user(id)
);
CREATE TABLE point_log (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, member_id BIGINT NOT NULL, change_points INT NOT NULL, balance_after INT NOT NULL,
 order_id BIGINT, type VARCHAR(30) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(member_id) REFERENCES shop_user(id), FOREIGN KEY(order_id) REFERENCES shop_order(id)
);
CREATE TABLE audit_log (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, operator_id BIGINT, action VARCHAR(60) NOT NULL, detail VARCHAR(1000) NOT NULL DEFAULT '',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(operator_id) REFERENCES shop_user(id)
);
CREATE TABLE shop_setting (setting_key VARCHAR(50) PRIMARY KEY, setting_value VARCHAR(500) NOT NULL);
CREATE TABLE checkout_ticket (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, member_id BIGINT NOT NULL, cashier_id BIGINT,
 status VARCHAR(24) NOT NULL, payload_json TEXT NOT NULL, request_key VARCHAR(100) NOT NULL UNIQUE,
 order_id BIGINT, error_message VARCHAR(500), counter_no INT,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(member_id) REFERENCES shop_user(id), FOREIGN KEY(cashier_id) REFERENCES shop_user(id), FOREIGN KEY(order_id) REFERENCES shop_order(id),
 CONSTRAINT ck_ticket_status CHECK(status IN ('WAITING','PROCESSING','COMPLETED','FAILED','CANCELLED'))
);
CREATE INDEX idx_order_member ON shop_order(member_id,created_at);
CREATE INDEX idx_order_cashier ON shop_order(cashier_id,created_at);
CREATE INDEX idx_inventory_product ON inventory_log(product_id,created_at);
CREATE INDEX idx_ticket_status ON checkout_ticket(status,created_at);
INSERT INTO shop_setting(setting_key,setting_value) VALUES
 ('PRODUCT_DISCOUNT_RATE','1.00'),('FULL_REDUCTION_THRESHOLD','100.00'),('FULL_REDUCTION_AMOUNT','10.00'),('COUNTER_COUNT','2');
