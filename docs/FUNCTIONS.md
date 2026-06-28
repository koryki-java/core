---
menuTitle: "Standard Functions"
parent: "REFERENCE"
order: 4
---

# KQL Standard Functions

KQL's standard functions are **dialect-agnostic**: registered in `StandardFunctions`, they work
identically across DuckDB, Oracle, PostgreSQL, SQL Server, and Snowflake. Each dialect may add or
override functions on top of this set.

The per-category reference pages below are **generated from the function catalog** — do not edit
them by hand (see [*Maintaining these docs*](#maintaining-these-docs)).

## Function reference

| Category | Representative functions |
|----------|--------------------------|
| [Mathematical](functions/math.md) | `abs`, `ceil`, `floor`, `round`, `mod`, `power`, `sqrt` |
| [String](functions/string.md) | `length`, `upper`, `lower`, `trim`, `substr`, `concat`, `replace`, `split_part` |
| [Pattern matching](functions/pattern_matching.md) | `regexp_like`, `regexp_count`, `regexp_substr`, `regexp_replace` |
| [Data type formatting](functions/formatting.md) | `to_char`, `to_number` |
| [Date/Time](functions/datetime.md) | `now`, `today`, `year`…`second`, `*_add`, `*_begin`/`*_end`, `*_between`, `calendar_distance` |
| [Conditional](functions/conditional.md) | `coalesce`, `case` |
| [Aggregate](functions/aggregate.md) | `count`, `avg`, `sum`, `min`, `max`, `string_agg` |
| [Type conversion](functions/conversion.md) | `to_date`, `to_timestamp`, `to_integer`, `to_decimal`, `to_text`, … |
| [Arithmetic operators](functions/arithmetic.md) | `+`, `-`, `*`, `/`, `negate` |
| [Comparison operators](functions/comparison.md) | `=`, `<`, `<=`, `>`, `>=`, `LIKE`, `BETWEEN`, `IN`, `ISNULL` |
| [Logical operators](functions/logical.md) | `AND`, `OR`, `NOT` |
| [Other](functions/other.md) | `at_zone`, `to_utc` |

Temporal semantics — the result-type algebra, duration arithmetic, and time zones — are normative
in [TEMPORAL.md](TEMPORAL.md); the Date/Time page above is the per-function reference.

## Per-dialect support

Each dialect's support matrix — the status (`standard` / `overridden` / `native` / `missing` /
`unsupported`) and the actual rendering of every catalog function — is generated too:

[DuckDB](dialects/duckdb.md) · [Oracle](dialects/oracle.md) · [PostgreSQL](dialects/postgresql.md) · [SQL Server](dialects/mssql.md) · [Snowflake](dialects/snowflake.md)

## Dialect-specific parse functions

`parse_date`, `parse_time`, and `parse_timestamp` take a value and an explicit format string and
are **dialect-specific**: each maps to the target database's native parser, so the format string
must follow that database's conventions. Format codes differ — DuckDB uses `strptime` codes
(`%d`, `%m`, `%Y`) while Oracle / PostgreSQL / Snowflake use `DD`, `MM`, `YYYY` — so a mask written
for one dialect is **not portable** to another. The per-dialect rendering is listed in each dialect
support page above. For ISO-8601 input, prefer the portable casts `to_date` / `to_time` /
`to_timestamp`.

## Maintaining these docs

The pages under `docs/functions/` and `docs/dialects/` are build artifacts of the function catalog,
regenerated and drift-checked by `FunctionDocsTest` — never edit them by hand. To change them, edit
the documentation metadata (`.doc()`, `.paragraph()`, `.example()`) on the definitions in
`core/src/main/java/ai/koryki/iql/functions/catalog/*Functions.java`, then regenerate:

```sh
DOCS_WRITE=true ./gradlew :tools:test --tests '*FunctionDocsTest*'
```

- Order within a page follows **registration order** — reorder the `register(...)` calls to reorder
  the page.
- Coverage worklist (functions missing a description or example):
  `./gradlew :tools:coverageReport -q`.
