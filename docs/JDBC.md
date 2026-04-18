# Package `ai.koryki.jdbc`

A thin abstraction layer over JDBC that separates SQL execution from result consumption.
The execution side prepares and runs statements; the consumer side decides what to do with
the rows — accumulate in memory, stream to CSV, or stream to XML.



## Type hierarchy

```
AutoCloseable
  └── ResultConsumer<C extends ColumnInfo>          base consumer
        └── ResultProcessor<C extends ColumnInfo>   adds row-by-row append
              ├── ListResult<C>                     in-memory accumulator
              ├── CSVFileResult<C>                  streaming CSV writer
              └── XMLFileResult<C>                  streaming XML writer

Database<C extends ResultConsumer<?>>               execution contract
  └── JdbcDatabase<C extends ResultProcessor<?>>    JDBC implementation

ColumnInfo                                          single-column metadata
SQLType                                             package-private JDBC type code enum
```



## Execution flow

```
Database.execute(sql, Supplier<C>)       ←     primary entry point
  creates C via supplier                       try-with-resources
  └─→ prepares PreparedStatement
        └─→ execute(PreparedStatement, C)
              ResultSet → processor.metadata(meta)
              for each row:
                read columns → List<Object>    date/time normalised to java.time
                processor.append(row)    ←     return false to stop early
          returns C
```


## Class overview

| Type | Kind | Role |
|---|---|---|
| `Database<C>` | interface | Contract for SQL execution; primary API via `execute(sql, Supplier<C>)`. |
| `JdbcDatabase<C>` | class | `Database` backed by a `java.sql.Connection`; normalises date/time types. |
| `ResultConsumer<C>` | interface | Base consumer: receives column metadata; lifecycle via `AutoCloseable`. |
| `ResultProcessor<C>` | interface | Extends `ResultConsumer`; adds per-row `append()` and `formatRow()` helper. |
| `ColumnInfo` | interface | Single-column metadata: display header and cell-to-string formatting. |
| `ListResult<C>` | class | Accumulates all rows in memory; renders to CSV string on demand. |
| `CSVFileResult<C>` | class | Streams each row immediately to a CSV file as it arrives. |
| `XMLFileResult<C>` | class | Streams each row immediately to an XML file (`<Result><Row><Cell>`). |
| `SQLType` | enum | Maps `java.sql.Types` integer codes to named constants; package-private. |



