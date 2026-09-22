# Security policy

## Supported versions

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

You will get an acknowledgement, then an assessment of whether the vulnerability is confirmed and
how severe it is. A confirmed one gets a fix and an advisory, and you are credited unless you prefer
not to be; details become public once the fix is released, and after 90 days at the latest. The
project is maintained by one person only, so there are no guaranteed response times.

## Scope

**In scope**, most of all the SQL this library writes:

- **Rendered SQL that reaches beyond the query.** A query, catalog or identifier that makes the
  transpiler emit SQL doing something the query did not ask for — injection through an identifier,
  a literal, a type or a catalog name — is the worst thing this project can get wrong. If you have
  an input that escapes the rendering, that is the report to send.
  [`docs/INJECTION.md`](./docs/INJECTION.md) names every path text can take into the rendered SQL
  and the barrier that stops it at each one.
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

## Releases

Release artifacts are built, signed and uploaded only by the release workflow in GitHub Actions, and
Maven Central rejects unsigned uploads. The signing key is the ed25519 key

    DBB3 44DB C587 AA7A 8A3B  7BB2 334E 2F44 3522 85EC

published on keys.openpgp.org. [`docs/RELEASES.md`](./docs/RELEASES.md) shows how to verify a
download against it, and what else protects the path from a commit to a published artifact.
