CREATE TABLE meetup_slots (
    id TEXT PRIMARY KEY NOT NULL,
    transaction_id TEXT NOT NULL REFERENCES transactions(id),
    start_at INTEGER NOT NULL,
    end_at INTEGER NOT NULL,
    location TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    CHECK (end_at > start_at)
);

CREATE INDEX meetup_slots_by_sale ON meetup_slots(transaction_id);

CREATE TABLE meetups (
    id TEXT PRIMARY KEY NOT NULL,
    transaction_id TEXT NOT NULL REFERENCES transactions(id),
    start_at INTEGER NOT NULL,
    end_at INTEGER NOT NULL,
    location TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED')),
    created_at INTEGER NOT NULL,
    CHECK (end_at > start_at)
);

CREATE UNIQUE INDEX one_scheduled_meetup_per_sale ON meetups(transaction_id) WHERE status = 'SCHEDULED';

CREATE TABLE meetup_reschedule_proposals (
    id TEXT PRIMARY KEY NOT NULL,
    meetup_id TEXT NOT NULL REFERENCES meetups(id),
    proposer_id TEXT NOT NULL REFERENCES users(id),
    start_at INTEGER NOT NULL,
    end_at INTEGER NOT NULL,
    location TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')),
    resolved_at INTEGER,
    CHECK (end_at > start_at),
    CHECK ((status = 'PENDING') = (resolved_at IS NULL)),
    CHECK (resolved_at IS NULL OR resolved_at >= created_at)
);

CREATE UNIQUE INDEX one_pending_move_per_meetup ON meetup_reschedule_proposals(meetup_id)
    WHERE status = 'PENDING';
