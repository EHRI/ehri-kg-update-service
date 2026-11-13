CREATE TABLE IF NOT EXISTS events_history (
    event_id VARCHAR NOT NULL,
    event_type VARCHAR NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    item_id VARCHAR NOT NULL,
    item_type VARCHAR NOT NULL,
    executed_queries VARCHAR,
    rdf_diff VARCHAR,
    errors VARCHAR
);

CREATE INDEX IF NOT EXISTS events_history_event
    ON events_history (event_id, item_id, item_type, event_type, timestamp);
