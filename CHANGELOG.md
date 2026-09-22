# Changelog

All notable changes to this project are documented here.

## [0.1.0] - unreleased

Initial release. Publishes thirteen signed artifacts to Maven Central under `ai.koryki.core`.

**The language and the transpiler**

- `koryki-core` — turns KQL and IQL into SQL: the intermediate query model, the rewrite rules, the
  type system and function catalog, the validators, and a JDBC execution layer.
- `koryki-kqlcore` — the KQL grammar and the ANTLR lexer and parser generated from it.

**The dialects**, each rendering the intermediate model as one engine's SQL and adapting its JDBC
driver: `koryki-duckdb`, `koryki-postgresql`, `koryki-oracle`, `koryki-snowflake`, `koryki-trino`,
`koryki-mariadb` (also MySQL), `koryki-mssql` and `koryki-sqlite`.

**Support**

- `koryki-northwind` — ready-made services for the Northwind sample database, over the catalog and
  data published from [koryki-java/northwind](https://github.com/koryki-java/northwind).
- `koryki-testkit` — the JUnit harness for the shared KQL/IQL fixture corpus.
- `koryki-tools` — build-time documentation tooling and a function-catalog audit.

### Security

The rendered SQL is the security boundary: every value a query carries is written into the
statement as a literal, so the escaping in the renderers is what stands between a query and an
injection. That surface is now documented and tested rather than assumed.

- [`docs/INJECTION.md`](./docs/INJECTION.md) names the four paths author-controlled text can take
  into the rendered SQL, the barrier at each one, and the rules a change has to follow.
- Two defects found and closed before the first release: `to_interval`'s unit argument reached the
  statement unquoted on the DuckDB dialect family, and a carriage return in a query's leading
  comment ended the emitted `--` comment early, leaving the rest of the description standing as
  SQL in front of the query.
- A third, found by generated input: a KQL literal ending in a backslash rendered an unterminated
  SQL literal, because the lexer and the renderer disagreed about what `\'` means.
- Regression tests for all four paths, across all eight dialects, each verified to fail when the
  defence it covers is removed — including per-engine literal escaping, which differs for MariaDB
  and Snowflake.

### Build and release

Java 21. Every jar ships sources, javadoc, `LICENSE` and `NOTICE`, and JAR
specification/implementation manifest attributes; archives are built with fixed timestamps and file
order, so the same commit gives the same bytes. Code is formatted with Spotless
(`google-java-format`, AOSP style) and JaCoCo reports coverage for every module. Releases are built
and signed in CI with an in-memory PGP key and published to Maven Central through the Central
Portal; see [`SECURITY.md`](./SECURITY.md) for the signing key and how to verify a download.

[0.1.0]: https://github.com/koryki-java/core/releases/tag/v0.1.0
