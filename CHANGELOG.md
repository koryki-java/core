# Changelog

All notable changes to this project are documented here.

## [unreleased]

### Added

- Nested values: postfix selectors and lambdas for lists, structs, maps and JSON. `t.tags@1`,
  `t.tags@-1`, `t.tags@2:`, `t.dims@w`, `t.attrs@'a key'`, `t.doc@a@b@1` select from a value of the
  type the column has (positions count from 1, from the end when negative, NULL where nothing is);
  `list_transform`, `list_filter` and `list_reduce` take a lambda, `list_transform(t.scores, (x, i)
  -> x * i)`. The grammar, the type model (ARRAY, STRUCT, MAP and JSON as a tree of types with
  `TypeSyntax`, `TypePath` and the recursive `Column`), typing, validation and rendering for every dialect
  are in, with a complex-types test bed per dialect (DuckDB, PostgreSQL, Trino, Snowflake, Oracle;
  SQL Server, MariaDB and SQLite for JSON). `docs/NESTED.md` says what is promised and what each
  dialect does. A lambda parameter is used by the name it is declared with, `x -> x * 2`, and a
  selection from it is `it@price`; where a function argument is a bare name it is the parameter of
  the lambda that declares it, and otherwise an alias as before. A parameter may not be named like an
  alias in sight, nor like the parameter of the lambda around it.

- A column says its type in a concept, and a concept says what a container is made of: a STRUCT
  has `entries` (its fields, in order), an ARRAY the entry `element` (and an `arraySize`), a MAP the
  entries `key` and `value`. An entry is the spelling of a type (`"VARCHAR"`), the name of a concept
  of the schema, or a concept of its own, written as an object, that belongs to this entry alone;
  every part can carry a label, a unit, a quantity, a presentation and an encoding. A container
  stored as JSON (JSON, JSONB, VARIANT, VARCHAR, TEXT) has the encoding `JSON`. `dialectType` of a
  container, if it is a spelling, is checked against its entries. A size parameter may be `MAX`
  (`NVARCHAR(MAX)`) or carry Oracle's unit (`VARCHAR2(10 CHAR)`).

- A model says in its own words what a concept says in the language of the schema: the members of
  an attribute and the notions of the model carry the label, comment and description of the parts
  of a nested column and of a shared concept, and `SchemaToModel` drafts them from the schema.
  Selectors write the names a model gives to the fields of a struct (`t.address@position@lat`),
  through blocks and inside EXISTS, and `KQLFormatter` translates them between models.
  `WordingAudit.audit(schema, model)` lists what a model has not said in its own words, as missing
  or still the words of the schema.
  `Translator.selectors`, `DictionaryTranslator` with a schema and `LinkResolver.dictionary(from, to,
  schema)` are the API of that translation. A column that refers to a shared concept is audited like
  any other, and its parts are the notion's to say unless the attribute has members of its own.

- `docs/VERIFICATION.md` says which dialects the continuous integration runs live (DuckDB, SQLite) and
  which are verified by hand before a release, and records the result of each release;
  `scripts/test-matrix.sh` prints tests, skipped and failed of every module from the last run.

- The bed with a concept that columns share, `check_shared`, on every dialect, in the catalogs of the
  northwind repository: `home` and `work`, and the elements of `route`, are one concept `point`.
  `SharedConceptContract` in the `testkit` is the one set of checks every dialect runs against it
  (the same answers through the English model and through the German names of the notion), and a
  dialect test extends it.

- The nodes of a nested column carry labels, descriptions, units, quantities and presentations, and
  `ConceptNodes.node` finds the concept a selector chain ends at, so what a consumer explains a nested
  result column by is in the catalog (the complex catalogs have it on every part). The flatten views
  inherit the annotations of the element. A node whose children do not fit its family (an ARRAY with
  fields) is refused when the catalog is read.

- A JSON column that stores a map (PostgreSQL, Oracle) can say so: `value`, and `key` INTEGER when the
  keys are integers written as text, make `t.attrs@color` a text and `t.lookup@1` the member `"1"`,
  as on a dialect with maps. What the catalog declares about JSON also survives `coalesce` and `CASE`
  when every branch declares the same, and the same elements in a fixed size and in a list reconcile
  to a list. A lambda that is a predicate (`list_filter`, `list_any`, `list_all`) has to answer yes
  or no, and the message for a JSON value where a list or a number is wanted says what to do.
  `NestedSupport.comparable` declares the nested values an engine takes in a comparison, a grouping,
  an ordering or `min`/`max` (Oracle takes no collection), and a query that does it is a positioned
  violation instead of an ORA error.

- Typed JSON: a catalog declares the content of a JSON column as a container concept with the
  encoding `JSON` (`n` INTEGER and `a.b` an array of INTEGER), and a selection that reaches one is
  that scalar, so `FILTER t.doc@n > 0`, arithmetic, `sum` and `GROUP BY` work without a cast.
  `TypeDescriptor.shape()` carries the declared content of a JSON type; the complex catalogs
  declare `doc`.

- A JSON array declared as a list of scalars is a list: the list functions and lambdas apply to
  `t.doc@a@b` on DuckDB, PostgreSQL, Trino, Snowflake and (without lambdas) Oracle. New
  `SqlDialect.jsonList` and `declaredScalarType` hooks.

- `SqlDialect.nestedSupport()` declares what an engine can do with nested values (the kinds of value
  a selector may reach into, JSON positions from the end, a lambda over a list made from a JSON array).
  A use it cannot express is now a positioned `unsupported` violation of the query
  (`NestedSupportValidator`) instead of an exception while rendering, and the support table in
  `docs/NESTED.md` is generated from the declarations (`./gradlew :tools:nestedMatrix`).

- Flatten views: `FlattenViews` (module `tools`, task `flattenViews`) generates, for a list column,
  the view with one row per element (parent key, `ordinal`, struct fields or `value`) in the SQL of
  DuckDB, PostgreSQL, Trino, Snowflake and Oracle, plus the catalog and model entries and the relation
  to the parent. The complex test beds carry three of them; see `docs/NESTED.md`. For SQL Server,
  MariaDB and SQLite the generator flattens an array declared inside a JSON column (`doc:.a.b`).

- A JSON value can be cast to the scalar it holds: `to_boolean`, `to_float`, `to_double`,
  `to_integer`, `to_bigint`, `to_smallint`, `to_decimal`, `to_text` and `to_varchar` accept JSON, so
  `FILTER to_integer(t.doc@n) > 0` and `to_text(t.attrs@color) = 'red'` work on every dialect that
  has JSON. New `SqlDialect.jsonScalar` and `selectionScalar` hooks; a dialect with JSON implements
  them.

- What a list answers about itself: `list_size`, `list_contains`, `list_sum`, `list_min`, `list_max`
  and `list_avg`, on DuckDB, PostgreSQL, Trino, Snowflake and Oracle, and `x IN t.tags` for the
  membership of a value in a list column. `length(list)` is refused and names `list_size`.

- Shaping and making lists: `list_sort`, `list_distinct`, `list_concat`, `list_any`, `list_all`,
  `list(a, b, ...)` and the aggregate `list_agg`, on DuckDB, PostgreSQL, Trino, Snowflake and Oracle.

- The parts of a nested column have names in the language of a model: `Attribute.members` and
  `Member.members` say, by the physical key of the entry, what the language calls a field of a struct
  and how it is worded (`Member` has a `name`, a label, a comment, a description and members of its
  own), and `Model.notions` says the same once for a concept the columns refer to by name.
  `MemberNames` walks the concept and the names together and answers both ways, and the resolver of
  a model checks them against the schema: a member is an entry of its concept, no two fields have the
  same name, a notion is a notion of a concept the schema has.

### Changed

- **Breaking: a column has a concept, and nothing else that says its type or its wording.** The
  fields `label`, `comment`, `description`, `typeFamily`, `typeEncoding`, `dialectType`, `fields`,
  `element`, `key`, `value`, `length`, `unit`, `quantity` and `presentation` are gone from
  `ai.koryki.catalog.schema.Column`, which keeps `name`, `nullable`, `pkPos`, `namePos` and either
  `concept` (its own) or `conceptRef` (the name of a concept in `Schema.concepts`). A `db.json` with
  the old fields no longer loads; rewrite it with `MigrateCatalogs`, which is in the history of
  this repository before the fields were removed (find it with `git log -S MigrateCatalogs`; it ran
  as `./gradlew :tools:migrateCatalogs`). `TypeDescriptorParser.parse(Column)` is `parse(where, family, encoding, dialectType)` for a
  scalar; the containers are typed by `ConceptResolver`. This release needs the catalogs of
  `koryki-northwind` 0.2.3 and a `koryki-derivation` that reads concepts; with the released
  derivation or the catalogs of `koryki-northwind` 0.2.1 or older it does not work.

- For dialect authors: a step of a selector keeps the name as it was written, `Selector.written()`,
  and says the entry of the schema it goes to, `Selector.key()`, which differs where a model gives a
  struct field another name. A dialect that reads the name of a step must read `key()`; the helpers
  of `NestedSql` do. `Selector` has no `key` property in JSON.

- The scripts that create the Northwind database for PostgreSQL, MariaDB, Oracle, SQL Server,
  Snowflake and SQLite, their `BuildNorthwind` loaders, `Script`, `ExportJson` and the committed
  `northwind.sqlite` moved to the northwind repository (modules `postgresql`, `mariadb`, `oracle`,
  `mssql`, `snowflake`, `sqlite`, `loader`), so a change of the Northwind schema is a release of that
  project only. The SQLite suites now take `northwind.sqlite` from `koryki-northwind-sqlite`; the
  `*NorthwindScript` Gradle tasks are gone.

### Fixed

- An aggregate over a selection, `sum(t.scores@2)`, was not taken for an aggregate: the other
  items of the query were not grouped. It is now, like an aggregate over a column.
- An element of an Oracle collection was the element of the row before it when the collection was
  empty: `t.route@2@lat`, a scalar subquery over `TABLE(list)`, took the cached answer of an earlier
  row for an empty list (measured on Oracle 26ai). A position now carries a test of the collection
  for NULL, `... WHERE E1.RN = 2 AND t.route IS NOT NULL`, which ends it. The SQL of every position
  on an Oracle collection changes with it; a golden of your own has to be written again.

## [0.2.1] - 2026-09-30

### Added

- A signature for the SQL: `Engine.withSignature(String)` (and `EngineBuilder`, `Generator`) writes a
  comment right behind the first `SELECT`, so a statement handed to a database or to an external
  system, such as a hosted MCP server, can be traced back to whoever produced it. `SqlRenderer`
  gets `setSignature` as a default that does nothing, so a renderer a caller makes itself keeps
  working; `SqlQueryRenderer` writes it.

### Fixed

- A block could not correctly reference an earlier sibling block — only self-reference (a
  recursive CTE) rendered correctly. `BlockRecursionDetector` treated any reference to a name that
  is not a catalog entity as recursion, so a plain chain onto an earlier block wrongly emitted
  `WITH RECURSIVE`; `InferJoinColumnsToBlockRule` added an inferred join column to the block
  currently being rendered instead of the block actually referenced, so a chain through a `VIA`
  join failed to render at all (`missing joinColumn`) unless the referenced block happened to
  already select that column. Both now compare the referenced name against the block's own id
  instead of only asking whether it is a catalog entity. Verified end to end against DuckDB,
  SQLite, PostgreSQL, MariaDB, Oracle and Trino with three new fixtures (`block_chain`,
  `block_chain_join`, `block_chain_three_deep`).
- A `select` used as an expression — a scalar subquery in a FETCH item, in a FILTER comparison,
  anywhere an expression may stand — reformatted to KQL text that failed to reparse, and, when
  correlated to an outer alias, to text the formatter itself rejected with `missing source`.
  `KQLFormatter` always branched a nested select off its own root scope instead of the enclosing
  select's, dropping the parentheses the grammar requires and any outer alias to correlate
  against. It now goes through the same per-select scope (`subSelect`) `EXISTS` already used
  correctly. SQL rendering itself was never affected, only the formatter's KQL round-trip.
- `docs/functions/aggregate.md` described the aggregate functions as they were before `median`,
  `quantile_cont` and `quantile_disc`.

### Changed

- `koryki-northwind` 0.2.1, which carries the block-chain fixtures; `jackson-databind` 3.2.3.

## [0.2.0] - 2026-09-27

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
  `withInfo`, the visualize engine — is covered too.
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

[Unreleased]: https://github.com/koryki-java/core/compare/v0.2.1...HEAD
[0.2.1]: https://github.com/koryki-java/core/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/koryki-java/core/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/koryki-java/core/releases/tag/v0.1.0
