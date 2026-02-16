CREATE TABLE IF NOT EXISTS events
(
    id        SERIAL PRIMARY KEY,
    timestamp TIMESTAMP WITHOUT TIME ZONE,
    device    VARCHAR(20),
    eventname TEXT
)