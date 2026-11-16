CREATE TABLE events_history (
    event_id VARCHAR NOT NULL,
    event_type VARCHAR NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    item_id VARCHAR NOT NULL,
    item_type VARCHAR NOT NULL,
    executed_queries VARCHAR,
    rdf_diff VARCHAR,
    errors VARCHAR
);