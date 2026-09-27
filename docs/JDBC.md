# Package `ai.koryki.jdbc`

A thin abstraction layer over JDBC that separates SQL execution from result consumption.
The execution side prepares and runs statements; the consumer side decides what to do with
the rows — accumulate in memory, stream to CSV, or stream to XML.

The execution side is not tied to JDBC: a `Database` may just as well be a service that takes one
statement and returns rows. See [A database that is not JDBC](#a-database-that-is-not-jdbc).



## Type hierarchy

```
AutoCloseable
  └── ResultConsumer<C extends ColumnInfo>          base consumer
        └── ResultProcessor<C extends ColumnInfo>   adds row-by-row append
              ├── ListResult<C>                     in-memory accumulator
              ├── CSVFileResult<C>                  streaming CSV writer
              └── XMLFileResult<C>                  streaming XML writer

Database<C extends ResultConsumer<?>>               execution contract
  ├── JdbcDatabase<C extends ResultProcessor<?>>    JDBC implementation
  └── (any other)                                   implements executeInto only

ColumnInfo                                          single-column metadata
SQLType                                             package-private JDBC type code enum
```



## Execution flow

```
Database.execute(sql, Supplier<C>)       creates C via supplier, try-with-resources
  └─→ Database.executeInto(sql, C)       ←     primary entry point, the one Engine calls
        JDBC (the default implementation):
        └─→ prepares PreparedStatement
              └─→ execute(PreparedStatement, C)
                    ResultSet → processor.metadata(meta)
                    for each row:
                      read columns → List<Object>    date/time normalised to java.time
                      processor.append(row)    ←     return false to stop early
        not JDBC (overrides executeInto):
        └─→ whatever the service does; each row → processor.append(row)
          returns C
```

`Engine` only ever calls `executeInto`, unless the caller passes a `Consumer<Statement>` to
configure the JDBC statement — that hook needs a statement, so it goes the JDBC route and a database
that has none refuses it with an `UnsupportedOperationException`.


## A database that is not JDBC

Implement `executeInto(String sql, C processor)` and `close()`; the two methods that take JDBC types
(`execute(String, Consumer<PreparedStatement>)` and `execute(PreparedStatement, C)`) are defaults
that refuse, so there is nothing to fake. Declare `C extends ResultProcessor<?>`, as `JdbcDatabase`
does.

What the implementation owes the processor:

- **Rows** — call `append` once per row, in column order, and stop when it returns `false`.
- **Values** as the read layer's own types: `String`, `Long`, `Double`, `BigDecimal`, `Boolean` and
  the `java.time` classes. A source that only has text — JSON dates, decimals as strings — is
  converted by the type the column has (`ColumnInfo.getTypeDescriptor()`), not by guessing from the
  value.
- **Columns** come from `processor.getInfos()`, which `Engine.executeKQL` fills from the query
  before the first row. `ResultProcessor.metadata` takes a `ResultSetMetaData` and is never called.
  A raw `executeSQL` has no infos, and so no types.

The entry point is called `executeInto` and is not an overload of `execute`: `ResultConsumer` has a
single abstract method, so `execute(sql, ListResult::new)` would fit `execute(String, C)` as well as
`execute(String, Supplier<C>)` and stop compiling.

Whether two calls may overlap is the database's to say: `allowsConcurrentExecution()`, `false` by
default. A `JdbcDatabase` wraps one connection, and statements in parallel on one connection are not
safe, so its callers serialize. A database that takes one statement per call and keeps nothing
between two — a stateless service — overrides it with `true`, and its callers stop queuing behind
each other. It says nothing about the processors: every call brings its own. Nor does it make the
rest of a call safe: calls that overlap must each render on a renderer of their own, because a
renderer keeps what it renders in fields. An `Engine` built from a renderer supplier does that by
itself; one built from a renderer instance does not (see
[IQL.md](IQL.md#a-renderer-is-for-one-caller-at-a-time)). The lock this flag takes away is what used
to hide that.


## Class overview

| Type | Kind | Role |
|---|---|---|
| `Database<C>` | interface | Contract for SQL execution. `executeInto(sql, C)` is the entry point every implementation offers; `execute(sql, Supplier<C>)` creates the processor for one call. The JDBC methods are optional defaults. |
| `JdbcDatabase<C>` | class | `Database` backed by a `java.sql.Connection`; normalises date/time types. |
| `ResultConsumer<C>` | interface | Base consumer: receives column metadata; lifecycle via `AutoCloseable`. |
| `ResultProcessor<C>` | interface | Extends `ResultConsumer`; adds per-row `append()` and `formatRow()` helper. |
| `ColumnInfo` | interface | Single-column metadata: display header and cell-to-string formatting. |
| `ListResult<C>` | class | Accumulates all rows in memory; renders to CSV string on demand. |
| `CSVFileResult<C>` | class | Streams each row immediately to a CSV file as it arrives. |
| `XMLFileResult<C>` | class | Streams each row immediately to an XML file (`<Result><Row><Cell>`). |



