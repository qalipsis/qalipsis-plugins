CREATE TABLE IF NOT EXISTS buildingentries
(
    id
    SERIAL
    PRIMARY
    KEY,
    timestamp
    DATETIME,
    action VARCHAR(20),
    username VARCHAR(20),
    enabled BOOLEAN
)
