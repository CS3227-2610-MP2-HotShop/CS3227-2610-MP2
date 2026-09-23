CREATE TABLE listings (
    id TEXT PRIMARY KEY NOT NULL,
    seller_id TEXT NOT NULL REFERENCES users(id),
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    category TEXT NOT NULL
        CHECK (category IN ('ELECTRONICS', 'BOOKS', 'CLOTHING', 'FURNITURE', 'SPORTS', 'OTHER')),
    price_cents INTEGER NOT NULL CHECK (price_cents BETWEEN 1 AND 100000000),
    condition TEXT NOT NULL CHECK (condition IN ('NEW', 'LIKE_NEW', 'GOOD', 'FAIR', 'POOR')),
    pickup_location TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('AVAILABLE', 'RESERVED', 'SOLD', 'ARCHIVED')),
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL CHECK (updated_at >= created_at)
);

CREATE INDEX listings_by_seller ON listings(seller_id);

CREATE INDEX listings_by_status ON listings(status);

CREATE TABLE listing_images (
    listing_id TEXT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    display_order INTEGER NOT NULL CHECK (display_order BETWEEN 0 AND 9),
    filename TEXT NOT NULL UNIQUE,
    PRIMARY KEY (listing_id, display_order)
);

CREATE TABLE image_cleanup_by_namespace (
    namespace TEXT NOT NULL CHECK (namespace IN ('profiles', 'listings')),
    filename TEXT NOT NULL,
    PRIMARY KEY (namespace, filename)
);

INSERT INTO image_cleanup_by_namespace(namespace, filename)
    SELECT 'profiles', filename FROM image_cleanup;

DROP TABLE image_cleanup;

ALTER TABLE image_cleanup_by_namespace RENAME TO image_cleanup;
