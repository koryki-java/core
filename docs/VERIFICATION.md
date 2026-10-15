# Verification: which dialects a release is tested on, and how that is recorded

A green build does not say that all eight dialects were tested. The continuous integration runs
without databases, so only the engines that live in the test process are really run there.
Everything else is verified before a release, and the result is a record that the build writes.

## What the continuous integration proves

| engine | in `build.yml` |
|---|---|
| DuckDB, SQLite | run live: the database is a file or runs inside the test process |
| PostgreSQL, MariaDB, Oracle, SQL Server, Trino, Snowflake | **skipped**: the tests need a server, and a class whose server is not reachable is skipped, not failed |

The rendering of every dialect (golden SQL, shape tests, hostile keys) is checked offline for all
eight. What only a live database shows is what the engine *answers*: the rows, the NULLs, the empty
collections. An Oracle scalar subquery that answered an empty list with the element of the row before
it was found only that way (see the changelog, 0.3.0).

## The record

The record of a release is [`verification/<version>.md`](./verification/), and nobody writes it. One
task makes it:

```
./gradlew verify
```

It runs the tests of every module anew, goes on past a failing module, and then writes the record
from the results, also when something failed. The record names

- the version, the date, the commit and the Java version,
- the version of `koryki-northwind` that core pins and the commit of the fixtures submodule,
- tests, skipped and failed of every module,
- the version each database says it is (`SELECT version()` and its kin, written by the
  `DatabaseVersionRecorder` suite of each dialect module).

A dialect is **verified** only when tests ran, none was skipped, none failed, and the database said
its version. A class whose server cannot be reached is skipped and the build is green anyway; the
record shows that as "not verified". The task refuses to write from a tree with changes in tracked
files (`-PallowDirty=true` overrides it for a trial run), and from results older than the commit.

## Before a release

1. Start the databases the suites expect. The connection comes from `~/.gradle/gradle.properties`,
   `<dialect>.northwind.url|user|password`; Snowflake signs in with `snowflake.privatekey`. The
   tables of the test beds come from the northwind repository: `./gradlew :<dialect>:createDatabase`
   there, and `:<dialect>:createShared` for a bed added later.
2. Run `./gradlew verify` on the commit that is to be tagged, from a Gradle daemon that can reach them.
3. Read the last line of the record: "Every module is verified", or the dialects that are not.
4. Commit `docs/verification/<version>.md`. Only `docs/`, the changelog, `.github/` and `scripts/` may change after that;
   `release.yml` refuses a tag whose record names another commit than the code it builds.

A dialect that could not be run stays "not verified" in the record, with its skipped tests as the
reason. That is a fact about the release, and the changelog may say so.
