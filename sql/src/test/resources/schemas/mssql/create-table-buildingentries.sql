CREATE TABLE buildingentries
(
    id        INT IDENTITY(1,1) PRIMARY KEY,
    [timestamp] DATETIME2,
    action VARCHAR(20),
    username VARCHAR(20),
    enabled BIT
)
