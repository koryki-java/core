# generic types in koryki

| Logical Type | Description                                | Cast                                         |    
|--------------|--------------------------------------------|----------------------------------------------|
| `BLOB`       | Binary data                                |                                              |  
| `BOOLEAN`    | true/false                                 | to_boolean                                   |  
| `DATE`       | Temporal Day                               | to_date(format)                              |  
| `DECIMAL`    | Decimal value with precision p and scale s | to_decimal(precision, scale)                 |
| `FLOAT`      | FLOAT                                      | to_float(size)                               |
| `INTEGER`    | Integer                                    | to_integer(size, unsigned)                   |   
| `INTERVAL`   | Temporal interval                          | to_interval(format)                          |   
| `JSON`       | json Document                              |                                              |   
| `TEXT`       | Text                                       | to_string(length, fixed, collation, charset) |  
| `TIME`       | Temporal Time                              | to_time(format)                              |  
| `TIMESTAMP`  | Temporal Timestamp                         | to_timestamp(format)                         |  
| `UUID`       | UUID                                       |                                              |  


## DuckDB

| DuckDB       | Logical Type | Size    |    
|--------------|--------------|---------|
| TINYINT      | INTEGER      | 1 byte  | 
| SMALLINT     | INTEGER      | 2 byte  | 
| INTEGER      | INTEGER      | 4 byte  | 
| BIGINT       | INTEGER      | 8 byte  | 
| HUGEINT      | INTEGER      | 16 byte | 
| UTINYINT     | INTEGER      | 1 byte  | 
| USMALLINT    | INTEGER      | 2 byte  | 
| UINTEGER     | INTEGER      | 4 byte  | 
| UBIGINT      | INTEGER      | 8 byte  | 
|              |              |         | 
| DECIMAL(p,s) | DECIMAL      |         | 
| NUMERIC(p,s) | DECIMAL      |         | 
|              |              |         | 
| REAL         | FLOAT        | x byte  | 
| FLOAT        | FLOAT        | x byte  | 
| DOUBLE       | FLOAT        | x byte  | 
|              |              |         | 
| BOOLEAN      | BOOLEAN      |         | 
|              |              |         | 
| CHAR(l)      | TEXT         |         | 
| VARCHAR      | TEXT         |         | 
| TEXT         | TEXT         |         | 
| STRING       | TEXT         |         | 
|              |              |         | 
| BLOB         | BLOB         |         | 
| BIT          | BLOB         |         | 
|              |              |         | 
| DATE         | DATE         |         | 
| TIME         | TIME         |         | 
| TIMESTAMP    | TIMESTAMP    |         | 
| TIMESTAMP_S  | TIMESTAMP    |         | 
| TIMESTAMP_MS | TIMESTAMP    |         | 
| TIMESTAMP_NS | TIMESTAMP    |         | 
| TIMESTAMPTZ  | TIMESTAMP    |         | 
| INTERVAL     | INTERVAL     |         | 
|              |              |         | 
| UUID         | UUID         |         | 
|              |              |         | 
| JSON         | JSON         |         | 


## PostgreSQL

| Logical Type   | PostgreSQL           |
|----------------|----------------------|
| `BIGINT`       | `BIGINT`             |
| `BLOB`         | `BYTEA`              |
| `BOOLEAN`      | `BOOLEAN`            |
| `DATE`         | `DATE`               |
| `DECIMAL(p,s)` | `NUMERIC(p,s)`       |
| `INTEGER`      | `INTEGER`            |
| `INTERVAL`     | `INTERVAL`           |
| `JSON`         | `JSONB`              |
| `TEXT`         | `TEXT`, `VARCHAR(n)` |
| `TIMESTAMP`    | `TIMESTAMP`          |
| `UUID`         | `UUID`               |


## MySql

| Logical Type | MySQL                      |
| ------------ | -------------------------- |
| INTEGER      | INT                        |
| BIGINT       | BIGINT                     |
| DECIMAL(p,s) | DECIMAL(p,s)               |
| BOOLEAN      | TINYINT(1)                 |
| VARCHAR(n)   | VARCHAR(n)                 |
| TEXT         | TEXT                       |
| DATE         | DATE                       |
| TIMESTAMP    | DATETIME                   |
| BINARY(n)    | VARBINARY(n)               |
| BLOB         | BLOB                       |
| JSON         | JSON                       |
| UUID         | CHAR(36)                   |
| INTERVAL     | ❌ (emuliert über INT/DATE) |

| Logical Type | MySql |    
|--------------|-------|
| `BIGINT`     |       |   
| `BLOB`       |       |   
| `BOOLEAN`    |       |   
| `DATE`       |       |   
| `DECIMAL`    |       |   
| `INTEGER`    |       |   
| `INTERVAL`   |       |   
| `JSON`       |       |   
| `TEXT`       |       |   
| `TIMESTAMP`  |       |   
| `UUID`       |       |   


## ORACLE Mapping

| Logical Type           | Oracle                                    |
| ---------------------- | ----------------------------------------- |
| INTEGER                | NUMBER(38)                                |
| BIGINT                 | NUMBER(19)                                |
| DECIMAL(p,s)           | NUMBER(p,s)                               |
| BOOLEAN                | NUMBER(1) oder CHAR(1)                    |
| VARCHAR(n)             | VARCHAR2(n)                               |
| TEXT                   | CLOB                                      |
| DATE                   | DATE                                      |
| TIMESTAMP              | TIMESTAMP                                 |
| BINARY(n)              | RAW(n)                                    |
| BLOB                   | BLOB                                      |
| JSON                   | CLOB + JSON constraint (oder native JSON) |
| UUID                   | RAW(16) oder VARCHAR2(36)                 |
| INTERVAL DAY TO SECOND | INTERVAL DAY TO SECOND                    |
| INTERVAL YEAR TO MONTH | INTERVAL YEAR TO MONTH                    |


| Logical Type | ORACLE |    
|--------------|--------|
| `BIGINT`     |        |   
| `BLOB`       |        |   
| `BOOLEAN`    |        |   
| `DATE`       |        |   
| `DECIMAL`    |        |   
| `INTEGER`    |        |   
| `INTERVAL`   |        |   
| `JSON`       |        |   
| `TEXT`       |        |   
| `TIMESTAMP`  |        |   
| `UUID`       |        |   



## SQL-Server

| Logical Type | SQL Server                      |
| ------------ | ------------------------------- |
| INTEGER      | INT                             |
| BIGINT       | BIGINT                          |
| DECIMAL(p,s) | DECIMAL(p,s)                    |
| BOOLEAN      | BIT                             |
| VARCHAR(n)   | VARCHAR(n)                      |
| TEXT         | VARCHAR(MAX)                    |
| DATE         | DATE                            |
| TIMESTAMP    | DATETIME2                       |
| BINARY(n)    | VARBINARY(n)                    |
| BLOB         | VARBINARY(MAX)                  |
| JSON         | NVARCHAR(MAX) + JSON functions  |
| UUID         | UNIQUEIDENTIFIER                |
| INTERVAL     | ❌ (emuliert über DATEADD logic) |

| Logical Type | SQL-Server |    
|--------------|------------|
| `BIGINT`     |            |   
| `BLOB`       |            |   
| `BOOLEAN`    |            |   
| `DATE`       |            |   
| `DECIMAL`    |            |   
| `INTEGER`    |            |   
| `INTERVAL`   |            |   
| `JSON`       |            |   
| `TEXT`       |            |   
| `TIMESTAMP`  |            |   
| `UUID`       |            |   


## Snowflake


| Logical Type | Snowflake     |    
|--------------|---------------|
| `BIGINT`     | NUMBER(38,0)  |   
| `BLOB`       | BINARY(n)     |   
| `BOOLEAN`    | BIT           |   
| `DATE`       | DATE          |   
| `DECIMAL`    | NUMBER(p,s)   |   
| `INTEGER`    | NUMBER(38,0)  |   
| `INTERVAL`   | ❌             |   
| `JSON`       | NVARCHAR(MAX) |   
| `TEXT`       | VARCHAR(MAX)  |   
| `TIMESTAMP`  | TIMESTAMP_NTZ |   
| `UUID`       | VARCHAR(36)   |   


# Typecasts in koryki



| function  | target   |                                                                    |
|-----------|---------------------------------------------------------------------------------|
| `to_boolean` |        |
| `to_integer` |  |
| `to_decimal` |  |
| `to_timestamp` |  |
| `to_text` |  |
| `to_date` |  |
| `to_time` |  |
| `to_interval` |  |







