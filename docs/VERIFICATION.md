# Verification: which dialects a release is tested on, and how that is recorded

A green build does not say that all eight dialects were tested. The continuous integration runs
without databases, so only the engines that live in the test process are really run there.
Everything else is verified by hand before a release, and this page is where that is recorded.

## What the continuous integration proves

| engine | in `build.yml` |
|---|---|
| DuckDB, SQLite | run live: the database is a file or runs inside the test process |
| PostgreSQL, MariaDB, Oracle, SQL Server, Trino, Snowflake | **skipped**: the tests need a server, and a class whose server is not reachable is skipped, not failed |

The rendering of every dialect (golden SQL, shape tests, hostile keys) is checked offline for all
eight. What only a live database shows is what the engine *answers*: the rows, the NULLs, the empty
collections. An Oracle scalar subquery that answered an empty list with the element of the row before
it was found only that way (see the changelog, 0.3.0).

## What is done before a release

1. Start the databases the suites expect (the connection comes from `~/.gradle/gradle.properties`,
   `<dialect>.northwind.url|user|password`; Snowflake signs in with `snowflake.privatekey`). The
   tables of the test beds come from the northwind repository: `./gradlew :<dialect>:createDatabase`
   there, and `:<dialect>:createShared` for a bed added later.
2. Run everything, with every engine reachable, from a Gradle daemon that can reach them:

   ```
   ./gradlew test --continue
   scripts/test-matrix.sh
   ```

3. Read the **skipped** column. A dialect counts as verified only with `skipped` 0 and `failed` 0;
   a number of skipped tests there means the server was not reached, however green the build is.
4. Copy the table below into a new section for the release, with the date, the versions of the
   databases and the version of the northwind catalogs the run used.

A dialect that could not be run goes into the table as "not run", with the reason. That is a fact
about the release, and the changelog may say so.

## Records

### 0.3.0

Run on 2026-10-15 on the commit before the tag, against the catalogs of `koryki-northwind` 0.3.0, built
from the release branch of the northwind repository (`release/0.3.0`).

| module | tests | skipped | failed | database |
|---|---:|---:|---:|---|
| core | 1683 | 0 | 0 | none |
| duckdb | 447 | 0 | 0 | DuckDB 1.5.5.0, in the test process |
| sqlite | 364 | 0 | 0 | SQLite 3.53.4.0 (JDBC driver), a file |
| postgresql | 450 | 0 | 0 | PostgreSQL 17.10, container `postgres:17` |
| mariadb | 360 | 0 | 0 | MariaDB 11.8.8, container `mariadb:11.8` |
| trino | 428 | 0 | 0 | Trino 481, container `trinodb/trino:latest`, memory catalog for the nested beds |
| oracle | 428 | 0 | 0 | Oracle Database Free 23.26.2, container `gvenzl/oracle-free:latest` |
| mssql | 354 | 0 | 0 | SQL Server, on a separate host; version not recorded |
| snowflake | not run | | | the account was offline in this run (77 tests, 13 skipped). The last complete run was 443 tests, 0 skipped, on 2026-10-15 against the catalogs of `koryki-northwind` 0.2.2-SNAPSHOT. Run it again before the tag. |
| northwind | 97 | 0 | 0 | the catalogs, no database |
| tools | 96 | 0 | 0 | none |

The version of SQL Server and the Snowflake run are to be filled in by whoever has them.
