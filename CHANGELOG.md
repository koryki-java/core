# Changelog

All notable changes to this project are documented here.

## [unreleased]

**A database does not have to be JDBC**

- `Database.executeInto(sql, processor)` is the entry point `Engine` now calls. A database that is
  reached through a service instead of a connection implements it and `close()` and nothing else;
  the two methods that take JDBC types (`PreparedStatement`) are defaults that refuse. `JdbcDatabase`
  and every existing implementation are unchanged: the default `executeInto` is the JDBC route it
  always took.
- `Engine.executeSQL` and `executeKQL` with a `Consumer<Statement>` hook still work on a JDBC
  database; on any other they throw `UnsupportedOperationException` rather than ignore the hook.
- `Database.allowsConcurrentExecution()`, `false` unless a database says otherwise. A `JdbcDatabase`
  wraps one connection and stays `false`; a stateless service can answer `true`, and a caller that
  would otherwise queue its calls — one lock in front of one connection — no longer has to.

**A renderer is for one caller at a time, and a generator can make one for each**

- `SqlQueryRenderer` keeps the query it is rendering in fields, so one instance shared by two
  threads renders one into the other — about a quarter of the results, in a test with eight threads,
  were another query's SQL. This was nowhere written down; `Generator` said the opposite ("immutable,
  and therefore shareable"). It is now stated on `SqlRenderer`, `SqlQueryRenderer`, `Generator`,
  `Engine`, `EngineBuilder` and `Database.allowsConcurrentExecution`, and in `docs/IQL.md` and
  `docs/JDBC.md`. The renderer class itself is unchanged.
- `Generator`, `Engine` and `EngineBuilder` (and `EngineBuilder.headers`) take a
  `Supplier<? extends SqlRenderer>` in place of the renderer:
  `EngineBuilder.headers(database, resolver, () -> new SqlQueryRenderer(dialect, zone))`. Every
  operation — `toSql`, `executeKQL`, `validateKQL`, `warningsKQL`, `analyze` — asks it for one
  renderer and uses that one throughout, so an engine built this way can be built once and shared
  between threads. `getRenderer()` returns a new renderer on each call, so what asks it for one —
  `withInfo`, the visualise engine — is covered too.
- The four ready-made services in `koryki-northwind` (`NorthwindService`, `TemporalService`,
  `TypecheckService`, `OraViewService`) take a renderer supplier the same way, in every constructor
  and `build` factory: `NorthwindService.build(database, () -> new SqlQueryRenderer(dialect, zone))`.
- **Deprecated:** the constructors and `EngineBuilder.headers` of `Generator`, `Engine` and
  `EngineBuilder` that take a renderer instance, and the matching constructors and `build`
  factories of the four services. They work as before, and an engine built from one renders every
  query on that one instance, so it is for one caller at a time. Nothing is removed: existing code
  compiles unchanged, with deprecation warnings.

**A schema in front of the tables**

- `Schema.schemaPrefix` (`db.json`: `"schemaPrefix": "sales"`), optional. The renderer writes it in
  front of every base table — `FROM sales.customers c` — including joins and `EXISTS`
  sub-selects, and never in front of a block. It goes through `renderIdentifier` like every other
  catalog name. Absent, and blank, mean none: existing catalogs render exactly as before.

**Catalogs are written without their nulls**

- `Util.write` no longer writes properties whose value is `null`. A `db.json` or `model.json` written
  this way is smaller and reads back to the same catalog, and a property that a catalog does
  not use, such as `schemaPrefix`, simply is not there.

**`percent_rank`**

- `percent_rank()` — where a row sits along its window, from 0 (first) to 1 (last):
  `(rank - 1) / (rows in the window - 1)`. Requires an `ORDER` inside its `OVER` clause, the same
  rule `rank` and `dense_rank` already carry. Chosen from a survey of real analyst queries against
  a third-party financial MCP server, and measured directly against DuckDB, PostgreSQL, MariaDB,
  Oracle, SQL Server, SQLite and Trino (2026-09-27): native and identical on every one of them,
  Snowflake included per its own published reference. No dialect override was needed. `median` and
  `percentile_cont` were the other candidates from that same survey — see the next entry for why
  they were measured out at the time, and implemented anyway.

**`median`, `quantile_cont` and `quantile_disc`**

- `median(value: numeric)` — the middle value of the sorted inputs, the average of the two middle
  values when there is an even number of them. `quantile_cont(fraction, value)` — the value at
  *fraction* of the way through the sorted inputs, interpolating between the two nearest when it
  falls between them; `quantile_cont(0.5, x)` is `median(x)`. `quantile_disc(fraction, value)` — the
  same reading, but never interpolates: the result is always one of the actual input values, so its
  return type tracks the value argument's own type (`ARG1`) rather than always coming back
  fractional (`FLOAT`, `median`'s and `quantile_cont`'s type) the way an interpolated result must.
- These are the two candidates the `percent_rank` entry above measured against the same seven
  engines and did not qualify by that survey's own majority bar: DuckDB, PostgreSQL, Oracle and
  Snowflake support them natively, as `MEDIAN`/`PERCENTILE_CONT`/`PERCENTILE_DISC ... WITHIN GROUP`;
  MariaDB and SQLite have no ordered-set aggregate syntax at all, Trino's parser rejects
  `WITHIN GROUP` outright, and SQL Server accepts `PERCENTILE_CONT`/`PERCENTILE_DISC` only as window
  functions, never as a plain `GROUP BY` aggregate — a four-to-four split, not a majority.
  Implemented anyway, on explicit request: native rendering for duckdb/oracle/snowflake, one
  override for PostgreSQL (no native `MEDIAN`; `PERCENTILE_CONT(0.5) WITHIN GROUP` is exactly the
  same computation, not an approximation — measured equal to Oracle's native `MEDIAN` on the same
  input), and a clean `unsupported()` refusal on the other four — caught before a query ever
  reaches them, with a message naming the function, rather than a driver error after the fact.
- DuckDB's own `quantile_cont`/`percentile_cont` carries a real numeric quirk here, unrelated to
  koryki: computed over a fixed-scale `DECIMAL(18,2)` column, it truncates the interpolated result
  to that same scale instead of widening to full precision, so an exact `25.875` comes back `25.87`
  where PostgreSQL and Oracle both answer the precise value. Not a rounding-convention difference —
  round-half-even would give `25.88`, not `25.87` — DuckDB's ordered-set aggregates compute in the
  input column's own decimal scale and lose the digit past it. Marked `// ignore=duckdb` on the two
  affected fixtures, the same convention already used for a Snowflake rounding boundary in `avg`'s
  moving-average fixture.

### Fixed

- `NorthwindDuckdb.northwind()` and `NorthwindDuckdb.fromResource(String)` no longer copy the
  Northwind database to one path shared by every process, `/tmp/korykiai.duckdb`. Programs that
  started together replaced that file under each other, and each one that found it locked by
  another died with `Could not set lock on file` -- six started at once left one alive. The copy
  now goes to `korykiai-<pid>.duckdb` in `java.io.tmpdir`, a name no other process can have, and
  is removed when the JVM ends properly. Note the new location: it was `/tmp` regardless of the
  platform.
- The SQLite test fixture `NorthwindSqlite.fromResource` had the same problem with
  `/tmp/korykiai.sqlite`: processes deleted it under each other, and one that opened it while
  another was still copying found it empty (`missing db /tmp/korykiai.sqlite 0`). Its copy now goes
  to `korykiai-<pid>.sqlite` in `java.io.tmpdir` and is removed when the JVM ends properly.
  `SqliteDatabase.fromResource` is unchanged, so callers that choose their own file are unaffected.

## [0.1.0] - 2026-09-22

Initial release. Publishes thirteen signed artifacts to Maven Central under `ai.koryki.core`.

**The language and the transpiler**

- `koryki-core` — turns KQL and IQL into SQL: the intermediate query model, the rewrite rules, the
  type system and function catalog, the validators, and a JDBC execution layer.
- `koryki-kqlcore` — the KQL grammar and the ANTLR lexer and parser generated from it.

**The dialects**, each rendering the intermediate model as one engine's SQL and adapting its JDBC
driver: `koryki-duckdb`, `koryki-postgresql`, `koryki-oracle`, `koryki-snowflake`, `koryki-trino`,
`koryki-mariadb` (also MySQL), `koryki-mssql` and `koryki-sqlite`. Each brings `koryki-core` with
it at compile scope, so depending on a dialect is enough to compile against both.

**Support**

- `koryki-northwind` — ready-made services for the Northwind sample database, over the catalog and
  data published from [koryki-java/northwind](https://github.com/koryki-java/northwind).
- `koryki-testkit` — the JUnit harness for the shared KQL/IQL fixture corpus.
- `koryki-tools` — build-time documentation tooling and a function-catalog audit.

### Security

The rendered SQL is the security boundary: every value a query carries is written into the
statement as a literal, so the escaping in the renderers is what stands between a query and an
injection.

### Build and release

Java 21. Every jar ships sources, javadoc, `LICENSE` and `NOTICE`, and JAR
specification/implementation manifest attributes; archives are built with fixed timestamps and file
order, so the same commit gives the same bytes. Code is formatted with Spotless
(`google-java-format`, AOSP style) and JaCoCo reports coverage for every module. Releases are built
and signed in CI with an in-memory PGP key and published to Maven Central through the Central
Portal; [`docs/RELEASES.md`](./docs/RELEASES.md) shows how to verify a download against the
signing key.

[Unreleased]: https://github.com/koryki-java/core/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/koryki-java/core/releases/tag/v0.1.0
