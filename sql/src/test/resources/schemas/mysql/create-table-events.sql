CREATE TABLE IF NOT EXISTS events
(
    id        INT NOT NULL AUTO_INCREMENT,
    timestamp DATETIME,
    device    VARCHAR(20),
    eventname TEXT,
    primary key (id)
)