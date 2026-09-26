# Changelog

All notable changes to this project are documented here.

## [Unreleased]

### Fixed

- `NorthwindDuckdb.northwind()` and `NorthwindDuckdb.fromResource(String)` no longer copy the
  Northwind database to one path shared by every process, `/tmp/korykiai.duckdb`. Programs that
  started together replaced that file under each other, and each one that found it locked by
  another died with `Could not set lock on file` -- six started at once left one alive. The copy
  now goes to `korykiai-<pid>.duckdb` in `java.io.tmpdir`, a name no other process can have, and
  is removed when the JVM ends properly. Note the new location: it was `/tmp` regardless of the
  platform.

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
