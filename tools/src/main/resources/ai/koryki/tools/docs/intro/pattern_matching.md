
Four functions over regular expressions: `regexp_like` asks whether a pattern matches,
`regexp_count` how often, `regexp_substr` pulls out what matched, and `regexp_replace` writes
something else in its place. Plain wildcard matching with `%` and `_` is `LIKE`, on the *Comparison
Operators* page.

## The pattern itself is not translated

This is the one thing to know before writing a regular expression here. A mask for `to_char` is
written once and rewritten for each database; a **pattern is not**. It goes to the database exactly
as written, and the databases do not run the same regular-expression engine:

    PostgreSQL   POSIX ARE          Oracle    POSIX
    DuckDB       RE2                Trino     Joni
    MariaDB      PCRE-style         SQLite    none at all
    SQL Server   none before 2025

What that costs you in practice is small if you stay in the common part — literals, `.`, `*`, `+`,
`?`, ranges, anchors, groups, alternation. It bites at the edges: lazy quantifiers, look-around,
named groups and back-references exist in some engines and not others, and where they are missing
you rarely get an error, just a pattern that quietly matches nothing.

**Prefer POSIX classes over backslash shorthands.** `[[:digit:]]` works on every engine that has
regular expressions at all; `\d` does not — and on MariaDB and Snowflake a backslash inside a string
literal used to be eaten before the pattern ever reached the engine. That is fixed, but the class
form is still the portable one.

## Replacement covers every match

`regexp_replace(s, p, r)` replaces **all** matches, on every dialect. That is worth stating because
PostgreSQL and DuckDB replace only the first by default — KQL levels them so the same query gives
the same answer everywhere. There is deliberately no flags argument: it meant modifiers on two
databases, a numeric position on two others, and nothing at all on a fifth.

## Two dialects have no regular expressions

SQLite ships the `REGEXP` *operator* but no implementation for it — that needs a loadable extension
— and SQL Server gained regular expressions only in the 2025 release. All four functions are
therefore declared unsupported on both: a query using them is refused with a named message rather
than being answered wrongly. Everything else on this page is available everywhere.

## Case sensitivity comes from the column

Matching honours the column's collation, exactly as `LIKE` and `=` do. On a case-insensitive
collation `regexp_like(name, '^a')` will match `Alfreds`; on a case-sensitive one it will not. If
the answer must not depend on how the schema was created, normalise with `upper()` or `lower()` on
both sides rather than relying on a case-insensitivity flag — those flags are not portable either.
