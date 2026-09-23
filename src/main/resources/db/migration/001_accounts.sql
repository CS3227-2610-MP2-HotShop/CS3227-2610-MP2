CREATE TABLE users (
    id TEXT PRIMARY KEY NOT NULL,
    username TEXT NOT NULL,
    normalized_username TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    profile_image TEXT,
    preferred_pickup_location TEXT
);

CREATE TABLE credentials (
    user_id TEXT PRIMARY KEY NOT NULL REFERENCES users(id),
    algorithm TEXT NOT NULL,
    iterations INTEGER NOT NULL CHECK (iterations > 0),
    salt BLOB NOT NULL,
    password_hash BLOB NOT NULL
);

CREATE TABLE image_cleanup (
    filename TEXT PRIMARY KEY NOT NULL
);
