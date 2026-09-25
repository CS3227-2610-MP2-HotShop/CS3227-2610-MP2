CREATE TABLE conversations (
    id TEXT PRIMARY KEY NOT NULL,
    listing_id TEXT NOT NULL REFERENCES listings(id),
    buyer_id TEXT NOT NULL REFERENCES users(id),
    seller_id TEXT NOT NULL REFERENCES users(id),
    created_at INTEGER NOT NULL,
    buyer_read_sequence INTEGER NOT NULL DEFAULT 0 CHECK (buyer_read_sequence >= 0),
    buyer_opened_at INTEGER,
    seller_read_sequence INTEGER NOT NULL DEFAULT 0 CHECK (seller_read_sequence >= 0),
    seller_opened_at INTEGER,
    UNIQUE (listing_id, buyer_id),
    CHECK (buyer_id <> seller_id)
);

CREATE INDEX conversations_by_buyer ON conversations(buyer_id);

CREATE INDEX conversations_by_seller ON conversations(seller_id);

CREATE TABLE messages (
    id TEXT PRIMARY KEY NOT NULL,
    conversation_id TEXT NOT NULL REFERENCES conversations(id),
    sender_id TEXT NOT NULL REFERENCES users(id),
    sequence INTEGER NOT NULL CHECK (sequence >= 1),
    text TEXT NOT NULL CHECK (length(text) BETWEEN 1 AND 1000),
    sent_at INTEGER NOT NULL,
    UNIQUE (conversation_id, sequence)
);

INSERT INTO conversations (id, listing_id, buyer_id, seller_id, created_at, buyer_opened_at, seller_opened_at)
SELECT lower(substr(random_hex, 1, 8) || '-' || substr(random_hex, 9, 4) || '-' || substr(random_hex, 13, 4)
        || '-' || substr(random_hex, 17, 4) || '-' || substr(random_hex, 21, 12)),
    listing_id, buyer_id, seller_id, started_at, seen_at, seen_at
FROM (SELECT hex(randomblob(16)) AS random_hex, offers.listing_id, offers.buyer_id, listings.seller_id,
        MIN(offers.created_at) AS started_at, MAX(COALESCE(offers.closed_at, offers.created_at)) AS seen_at
    FROM offers JOIN listings ON listings.id = offers.listing_id
    GROUP BY offers.listing_id, offers.buyer_id)
