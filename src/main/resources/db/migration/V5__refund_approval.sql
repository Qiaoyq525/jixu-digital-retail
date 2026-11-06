-- 会员先申请，老板审核后才执行退款。旧订单保持原支付状态。
ALTER TABLE shop_order ADD COLUMN refund_status VARCHAR(16) NOT NULL DEFAULT 'NONE';
ALTER TABLE shop_order ADD COLUMN refund_reason VARCHAR(200) NOT NULL DEFAULT '';
ALTER TABLE shop_order ADD COLUMN refund_review_note VARCHAR(200) NOT NULL DEFAULT '';
ALTER TABLE shop_order ADD COLUMN refund_requested_at TIMESTAMP NULL;
ALTER TABLE shop_order ADD COLUMN refund_reviewed_at TIMESTAMP NULL;
ALTER TABLE shop_order ADD CONSTRAINT ck_refund_status CHECK(refund_status IN ('NONE','PENDING','APPROVED','REJECTED'));
