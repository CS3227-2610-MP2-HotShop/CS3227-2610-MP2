ALTER TABLE transactions ADD COLUMN cancelled_at INTEGER;

ALTER TABLE transactions ADD COLUMN cancelled_by TEXT REFERENCES users(id);

CREATE TABLE cancellation_requests (
    id TEXT PRIMARY KEY NOT NULL,
    transaction_id TEXT NOT NULL REFERENCES transactions(id),
    requester_id TEXT NOT NULL REFERENCES users(id),
    created_at INTEGER NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')),
    resolved_at INTEGER,
    CHECK ((status = 'PENDING') = (resolved_at IS NULL)),
    CHECK (resolved_at IS NULL OR resolved_at >= created_at)
);

CREATE UNIQUE INDEX one_pending_request_per_sale ON cancellation_requests(transaction_id)
    WHERE status = 'PENDING';

CREATE INDEX transactions_by_buyer ON transactions(buyer_id);

CREATE INDEX transactions_by_seller ON transactions(seller_id);
