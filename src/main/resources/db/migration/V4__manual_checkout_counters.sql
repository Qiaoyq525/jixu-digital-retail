-- 固定柜台与员工的对应关系，不因员工停用或营业柜台数变化而重新编号。
CREATE TABLE checkout_counter (
 counter_no INT PRIMARY KEY,
 cashier_id BIGINT UNIQUE,
 FOREIGN KEY(cashier_id) REFERENCES shop_user(id),
 CONSTRAINT ck_counter_number CHECK(counter_no BETWEEN 1 AND 4)
);
INSERT INTO checkout_counter(counter_no) VALUES (1),(2),(3),(4);
-- 旧库直接绑定原有收银员；空库的演示账号将在种子初始化后由 QueueService 绑定。
UPDATE checkout_counter SET cashier_id=(SELECT id FROM shop_user WHERE role='SALES' AND enabled=TRUE ORDER BY id LIMIT 1 OFFSET 0) WHERE counter_no=1;
UPDATE checkout_counter SET cashier_id=(SELECT id FROM shop_user WHERE role='SALES' AND enabled=TRUE ORDER BY id LIMIT 1 OFFSET 1) WHERE counter_no=2;
UPDATE checkout_counter SET cashier_id=(SELECT id FROM shop_user WHERE role='SALES' AND enabled=TRUE ORDER BY id LIMIT 1 OFFSET 2) WHERE counter_no=3;
UPDATE checkout_counter SET cashier_id=(SELECT id FROM shop_user WHERE role='SALES' AND enabled=TRUE ORDER BY id LIMIT 1 OFFSET 3) WHERE counter_no=4;
CREATE INDEX idx_ticket_counter_queue ON checkout_ticket(counter_no,status,id);
CREATE INDEX idx_ticket_cashier_queue ON checkout_ticket(cashier_id,status,id);
