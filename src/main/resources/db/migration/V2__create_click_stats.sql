-- One row per link per day. The day is the UTC date of the click (clickedAt), not the time
-- the consumer happened to read the event.
CREATE TABLE click_stats (
    code       VARCHAR(7) NOT NULL,
    day        DATE       NOT NULL,
    num_clicks BIGINT     NOT NULL,
    PRIMARY KEY (code, day)
);

-- Event IDs already counted. Inserting the same ID twice fails on the primary key, which is how
-- a redelivered event is detected. Rows older than 1 day are deleted by a scheduled job.
CREATE TABLE processed_events (
    event_id     UUID        PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Lets the cleanup job find old rows without scanning the whole table.
CREATE INDEX processed_events_processed_at_idx ON processed_events (processed_at);
