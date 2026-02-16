CREATE TABLE IF NOT EXISTS buildingentries
(
    id
    SERIAL
    PRIMARY
    KEY,
    timestamp
    TIMESTAMP
    WITHOUT
    TIME
    ZONE,
    action VARCHAR(20),
    username VARCHAR(20),
    enabled BOOLEAN
)
