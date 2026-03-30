CREATE TABLE events
(
    id        INT IDENTITY(1,1) PRIMARY KEY,
    [timestamp] DATETIME2,
    device    VARCHAR(20),
    eventname VARCHAR(MAX)
)
