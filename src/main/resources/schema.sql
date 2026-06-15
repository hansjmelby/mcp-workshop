-- Skjema for ferie-booking. Idempotent (IF NOT EXISTS) slik at appen tåler omstart.

CREATE TABLE IF NOT EXISTS destinations (
    id              INTEGER PRIMARY KEY,
    name            TEXT    NOT NULL,
    country         TEXT    NOT NULL,
    description     TEXT,
    price_per_night REAL    NOT NULL,
    available       INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS availability (
    id             INTEGER PRIMARY KEY,
    destination_id INTEGER NOT NULL REFERENCES destinations (id),
    start_date     TEXT    NOT NULL,   -- ISO-8601 (yyyy-MM-dd)
    end_date       TEXT    NOT NULL,
    capacity       INTEGER NOT NULL,
    season_price   REAL                -- valgfri sesongpris; NULL = bruk price_per_night
);

CREATE TABLE IF NOT EXISTS bookings (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_name  TEXT    NOT NULL,
    destination_id INTEGER NOT NULL REFERENCES destinations (id),
    start_date     TEXT    NOT NULL,
    end_date       TEXT    NOT NULL,
    num_travelers  INTEGER NOT NULL,
    total_price    REAL    NOT NULL,
    status         TEXT    NOT NULL DEFAULT 'PENDING'  -- PENDING|CONFIRMED|PAID|COMPLETED|CANCELLED
);
