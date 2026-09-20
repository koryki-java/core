# Security policy

## Supported versions

| Version | Security fixes |
|---|---|
| latest 0.x release | ✓ |
| older releases | ✗ — upgrade to the latest release |

Only the latest release receives fixes; there are no backports.

## Reporting a vulnerability

Please do **not** report a suspected vulnerability in a public issue, discussion or pull request.

Report it privately instead, through GitHub's private vulnerability reporting:
**[Security → Report a vulnerability](https://github.com/koryki-java/core/security/advisories/new)**.
Only the maintainer can see the report.

A report is easiest to act on when it names:

- the affected artifact and version (e.g. `ai.koryki.core:koryki-core:0.1.0`) or commit,
- what an attacker can achieve, and under which conditions,
- the KQL, catalog or steps to reproduce it,
- whether and when you intend to disclose it yourself.

## What happens next

- **Acknowledgement** that the report arrived.
- **Assessment** of whether the vulnerability is confirmed, and how severe it is (CVSS).
- **Fix and advisory** for a confirmed vulnerability. Reporters are credited unless they prefer not to be.
- **Coordinated disclosure:** details become public once the fix is released, and after 90 days at
  the latest.

The project is maintained by one person only; sorry, no guaranteed response times.

## Scope

This repository publishes a Java library that turns KQL and IQL into SQL and runs it over JDBC:

- `ai.koryki.core:koryki-core`, `koryki-kqlcore`, `koryki-tools` — the language, the transpiler and
  its tooling,
- `koryki-duckdb`, `koryki-sqlite`, `koryki-postgresql`, `koryki-mariadb`, `koryki-mssql`,
  `koryki-oracle`, `koryki-snowflake`, `koryki-trino` — the dialect and JDBC adapters,
- `koryki-northwind`, `koryki-testkit` — the reference database services and the test support.

**In scope**, most of all the SQL this library writes:

- **Rendered SQL that reaches beyond the query.** A query, catalog or identifier that makes the
  transpiler emit SQL doing something the query did not ask for — injection through an identifier,
  a literal, a type or a catalog name — is the worst thing this project can get wrong. If you have
  an input that escapes the rendering, that is the report to send.
- **Catalog and query handling.** A crafted `db.json`, `model.json` or query that makes the loader
  read or write outside its resources, or that exhausts memory or time out of proportion to its
  size.
- **The build and release process**, where a weakness could let someone alter a published artifact.

**Out of scope:**

- vulnerabilities in the databases and JDBC drivers this library talks to, or in the JDK and Gradle
  — report them upstream; this project updates once a fixed version exists,
- the rights of the database account a consumer hands to this library: a transpiled query runs with
  exactly those, and no library can lower them. Grant the account only what the queries need,
- the content of the Northwind sample data, which is fictional, and the test fixtures in
  [koryki-java/northwind](https://github.com/koryki-java/northwind).

## How releases are protected

- **Signed releases from CI only.** Release artifacts are built, signed with PGP and uploaded by the
  release workflow in GitHub Actions; a release runs from a tag matching the version in
  `gradle.properties`, and Maven Central rejects unsigned uploads. The signing key is the ed25519
  key `DBB3 44DB C587 AA7A 8A3B  7BB2 334E 2F44 3522 85EC`, published on keys.openpgp.org — see
  [Verifying a download](#verifying-a-download).
- **Secrets stay in the release workflow.** The signing key and the Maven Central credentials are
  GitHub secrets used only by the release workflow, which pull requests do not trigger.
- **Least privilege.** All workflows run with a read-only repository token.
- **Pinned build tool.** The Gradle wrapper pins the distribution's SHA-256, so a tampered Gradle
  download is refused, and every workflow checks `gradle-wrapper.jar` against Gradle's published
  checksums before running it.
- **Secrets kept out of the repository.** The `githooks/pre-push` hook rejects a push that carries a
  credential, and the `Secret scan` workflow runs gitleaks over the whole history on every push and
  once a week.
- **Stable archives.** Jars are built with fixed timestamps and file order, so the same sources give
  the same archives.

## Verifying a download

Every file on Maven Central has a signature next to it, the same name with `.asc` appended. To check
a jar against the release key:

```
# 1. Fetch the key once, by its full fingerprint -- not by name or short ID, which anyone can imitate.
gpg --keyserver hkps://keys.openpgp.org --recv-keys DBB344DBC587AA7A8A3B7BB2334E2F44352285EC

# 2. Download the jar and its signature.
curl -O https://repo1.maven.org/maven2/ai/koryki/core/koryki-core/0.1.0/koryki-core-0.1.0.jar
curl -O https://repo1.maven.org/maven2/ai/koryki/core/koryki-core/0.1.0/koryki-core-0.1.0.jar.asc

# 3. Verify.
gpg --verify koryki-core-0.1.0.jar.asc koryki-core-0.1.0.jar
```

The jar is genuine when gpg reports `Good signature` **and** the line `using EDDSA key` shows
`DBB344DBC587AA7A8A3B7BB2334E2F44352285EC`. The warning that the key "is not certified with a trusted
signature" is expected: it only means you have not signed the key yourself. What ties the key to this
project is the fingerprint, and it is stated here, in the repository, so that faking a release would
take both a forged key and a change to this file. `BAD signature` means the file changed after it was
signed; do not use it.

A Gradle build can do this check on every build through
[dependency verification](https://docs.gradle.org/current/userguide/dependency_verification.html)
with `<verify-signatures>true</verify-signatures>`: add the key to `<trusted-keys>` in
`gradle/verification-metadata.xml`.

```xml
<trusted-key id="DBB344DBC587AA7A8A3B7BB2334E2F44352285EC" group="ai.koryki.core"/>
```
