CREATE TABLE offers (
    id TEXT PRIMARY KEY NOT NULL,
    listing_id TEXT NOT NULL REFERENCES listings(id),
    buyer_id TEXT NOT NULL REFERENCES users(id),
    amount_cents INTEGER NOT NULL CHECK (amount_cents BETWEEN 1 AND 100000000),
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')),
    created_at INTEGER NOT NULL,
    closed_at INTEGER,
    CHECK ((status = 'PENDING') = (closed_at IS NULL)),
    CHECK (closed_at IS NULL OR closed_at >= created_at)
);

CREATE UNIQUE INDEX one_pending_offer_per_buyer ON offers(listing_id, buyer_id) WHERE status = 'PENDING';

CREATE INDEX offers_by_buyer ON offers(buyer_id);

CREATE TABLE transactions (
    id TEXT PRIMARY KEY NOT NULL,
    listing_id TEXT NOT NULL REFERENCES listings(id),
    accepted_offer_id TEXT NOT NULL UNIQUE REFERENCES offers(id),
    buyer_id TEXT NOT NULL REFERENCES users(id),
    seller_id TEXT NOT NULL REFERENCES users(id),
    agreed_price_cents INTEGER NOT NULL CHECK (agreed_price_cents BETWEEN 1 AND 100000000),
    listing_title TEXT NOT NULL,
    listing_description TEXT NOT NULL,
    listing_condition TEXT NOT NULL CHECK (listing_condition IN ('NEW', 'LIKE_NEW', 'GOOD', 'FAIR', 'POOR')),
    created_at INTEGER NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
    buyer_confirmed_at INTEGER,
    seller_confirmed_at INTEGER
);

CREATE UNIQUE INDEX one_active_sale_per_listing ON transactions(listing_id) WHERE status = 'ACTIVE';
