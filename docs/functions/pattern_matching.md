---
menuTitle: "Pattern Matching"
parent: "FUNCTIONS"
order: 6
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Pattern Matching

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

## regexp_like

`regexp_like(string: TEXT, pattern: TEXT [, flags: TEXT])` → BOOLEAN

True if the string matches the regular expression *pattern*.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the source text to test |
| pattern | TEXT | the regular expression to match against |
| flags | TEXT *(optional)* | match modifiers, e.g. 'i' for case-insensitivity |

Sample query:

```kql
// regexp_like: does the company name start with A?
FIND customers c
FETCH regexp_like(c.company_name, '^A') starts_with_a
```

### Generated SQL

**oracle · postgresql · trino**

```sql
-- regexp_like: does the company name start with A?
SELECT
  regexp_like(c.company_name, '^A') AS starts_with_a
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| duckdb | `regexp_matches(c.company_name, '^A') AS starts_with_a` |
| mariadb | `(c.company_name REGEXP '^A') AS starts_with_a` |
| snowflake | `(REGEXP_INSTR(c.company_name, '^A') > 0) AS starts_with_a` |

Unsupported: **mssql**, **sqlite**


## regexp_count

`regexp_count(string: TEXT, pattern: TEXT [, start: INTEGER])` → INTEGER

Number of matches of *pattern* in the string.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the source text to search |
| pattern | TEXT | the regular expression to count occurrences of |
| start | INTEGER *(optional)* | 1-based index to begin searching from |

Sample query:

```kql
// regexp_count: occurrences of 'sales' in the notes.
FIND employees e
FETCH regexp_count(e.notes, 'sales') sales_mentions
```

### Generated SQL

**oracle · snowflake · postgresql · trino**

```sql
-- regexp_count: occurrences of 'sales' in the notes.
SELECT
  regexp_count(e.notes, 'sales') AS sales_mentions
FROM
 employees e
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| duckdb | `len(regexp_extract_all(e.notes, 'sales')) AS sales_mentions` |

Unsupported: **mariadb**, **mssql**, **sqlite**


## regexp_substr

`regexp_substr(string: TEXT, pattern: TEXT)` → TEXT

First substring matching the regular expression *pattern*.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the source text to search |
| pattern | TEXT | the regular expression to match |

Sample query:

```kql
// regexp_substr: first run of digits in a phone number.
FIND customers c
FETCH regexp_substr(c.phone, '[0-9]+') first_digits
```

### Generated SQL

**oracle · snowflake · postgresql · mariadb**

```sql
-- regexp_substr: first run of digits in a phone number.
SELECT
  regexp_substr(c.phone, '[0-9]+') AS first_digits
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| duckdb · trino | `regexp_extract(c.phone, '[0-9]+') AS first_digits` |

Unsupported: **mssql**, **sqlite**


## regexp_replace

`regexp_replace(string: TEXT, pattern: TEXT, replacement: TEXT)` → TEXT

Replaces **every** substring matching the regular expression *pattern* with *replacement*.

Every match, not just the first — on every database. That guarantee costs something: PostgreSQL and DuckDB replace only the first match by default, so KQL renders them with the `g` modifier. MariaDB, Oracle and Trino already replace all. Measured before this was levelled, `regexp_replace(phone, '[^0-9]', '')` on `(02) 201 24 67` gave `02) 201 24 67` on the first two and `022012467` on the other three.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the source text to modify |
| pattern | TEXT | the regular expression to match |
| replacement | TEXT | the text substituted for each match |

Sample query:

```kql
// regexp_replace: strip non-digits from a phone number.
// Every match is replaced, on every dialect — the levelling that makes this sample mean what it says.
FIND customers c
FETCH regexp_replace(c.phone, '[^0-9]', '') digits_only
```

### Generated SQL

**oracle · snowflake · mariadb · trino**

```sql
-- regexp_replace: strip non-digits from a phone number.
-- Every match is replaced, on every dialect — the levelling that makes this sample mean what it says.
SELECT
  regexp_replace(c.phone, '[^0-9]', '') AS digits_only
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| duckdb · postgresql | `regexp_replace(c.phone, '[^0-9]', '', 'g') AS digits_only` |

Unsupported: **mssql**, **sqlite**

