# Releases: how they are protected, and how to verify one

Everything that ties an artifact on Maven Central to this repository. The security policy itself —
what is in scope and how to report a vulnerability — is in [`SECURITY.md`](../SECURITY.md).

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
project is the fingerprint, and it is stated in the repository — in `SECURITY.md` and here — so that
faking a release would take both a forged key and a change to those files. `BAD signature` means the
file changed after it was signed; do not use it.

A Gradle build can do this check on every build through
[dependency verification](https://docs.gradle.org/current/userguide/dependency_verification.html)
with `<verify-signatures>true</verify-signatures>`: add the key to `<trusted-keys>` in
`gradle/verification-metadata.xml`.

```xml
<trusted-key id="DBB344DBC587AA7A8A3B7BB2334E2F44352285EC" group="ai.koryki.core"/>
```

## What protects the release path

- **Signed releases from CI only.** Release artifacts are built, signed with PGP and uploaded by the
  release workflow in GitHub Actions; a release runs from a tag matching the version in
  `gradle.properties`, and Maven Central rejects unsigned uploads.
- **Secrets stay in the release workflow.** The signing key and the Maven Central credentials are
  GitHub secrets used only by that workflow, which pull requests do not trigger.
- **Least privilege.** All workflows run with a read-only repository token.
- **Pinned build tool.** The Gradle wrapper pins the distribution's SHA-256, so a tampered Gradle
  download is refused, and every workflow checks `gradle-wrapper.jar` against Gradle's published
  checksums before running it.
- **Secrets kept out of the repository.** The `githooks/pre-push` hook rejects a push that carries a
  credential, and the `Secret scan` workflow runs gitleaks over the whole history on every push and
  once a week.
- **Stable archives.** Jars are built with fixed timestamps and file order, so the same sources give
  the same archives.
