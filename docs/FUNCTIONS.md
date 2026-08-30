---
menuTitle: "Standard Functions"
parent: "REFERENCE"
order: 4
---

# KQL Standard Functions

KQL's standard functions are **dialect-agnostic**: registered in `StandardFunctions`, they work
identically across DuckDB, Oracle, PostgreSQL, SQL Server, and Snowflake. Each dialect may add or
override functions on top of this set.

## How far a dialect override may go

A dialect override exists so the same KQL answers the same question everywhere. How far it should
go is a trade-off between four things, and naming them makes each decision arguable rather than a
matter of taste:

1. **Conformity to expectation** — would someone who knows this database recognise the SQL?
2. **IO cost** of evaluating the query.
3. **CPU cost** of evaluating the query.
4. **Uniformity** of the answer across all dialects.

Criteria 1 and 4 pull against each other, but far less often than it seems. Emitting
`TO_CHAR(x, 'YYYY-MM-DD')` instead of a bare cast improves both: no Oracle developer expects a
generated query's answer to depend on the connecting session's `NLS_DATE_FORMAT`. Where they truly
conflict, the following order decides.

**Determinism comes first.** An answer must not depend on who connects or on the order rows happen
to arrive in. Without that nothing else is testable, and no other rule can hold. A session setting
that changes a result is a defect, never a dialect flavour.

**Where KQL has promised a semantics, it holds on every dialect — CPU is an acceptable price, IO is
not.** This is the line. Extra work per row buys a promise that the documentation already made;
an extra scan, a join against a helper table, or a sort that spills to disk does not. Correcting
Oracle's `ADD_MONTHS`, which promotes a month-end even where the target day exists, costs three
evaluations instead of one and no IO — so it is paid. Note that a function applied to a column in a
filter is not index-usable on any dialect to begin with, so such a correction cannot cost a plan
that existed; and the half-open interval pattern in [TEMPORAL.md](TEMPORAL.md) applies the function
to a constant, which is folded once.

**Where nothing is promised and the answers already agree, emit what a native would write.** This
covers the large majority of overrides, which are plain renamings. Criterion 1 wins wherever it is
free.

**What an engine cannot do is declared, not emulated.** SQLite folds only ASCII in `upper`;
reproducing Unicode case folding in SQL would mean a mapping table and therefore IO. Four engines
have no interval value type at all. These are declared in the catalog — `unsupported`, or
`SqlDialect.intervalSupport()` — which turns them into a positioned violation and a documented
fact instead of a surprise at runtime. This is where non-uniformity is accepted deliberately and
visibly.

Two things are outside the trade-off. Values that are non-deterministic by nature (`now`, `today`,
`random`) have no uniform answer to establish. And a difference that lives only in how a driver
spells a value back — Trino returning `0 01:02:03.000` for an interval — belongs in the decoder,
where it costs nothing in the database.

## Function reference

| Category                                          | Representative functions                                                                          |
|---------------------------------------------------|---------------------------------------------------------------------------------------------------|
| [Logical operators](functions/logical.md)         | `AND`, `OR`, `NOT`                                                                                |
| [Comparison operators](functions/comparison.md)   | `=`, `<`, `<=`, `>`, `>=`, `LIKE`, `BETWEEN`, `IN`, `ISNULL`                                      |
| [Arithmetic operators](functions/arithmetic.md)   | `+`, `-`, `*`, `/`, `negate`                                                                      |
| [Mathematical](functions/math.md)                 | `abs`, `ceil`, `floor`, `round`, `mod`, `power`, `sqrt`                                           |
| [String](functions/string.md)                     | `length`, `upper`, `lower`, `trim`, `substr`, `concat`, `replace`, `split_part`                   |
| [Pattern matching](functions/pattern_matching.md) | `regexp_like`, `regexp_count`, `regexp_substr`, `regexp_replace`                                  |
| [Data type formatting](functions/formatting.md)   | `to_char`, `to_number`                                                                            |
| [Date/Time](functions/datetime.md)                | `now`, `today`, `year`…`second`, `*_add`, `*_begin`/`*_end`, `*_between`, `calendar_distance`, `at_zone`, `to_utc` |
| [Conditional](functions/conditional.md)           | `coalesce`, `case`                                                                                |
| [Aggregate](functions/aggregate.md)               | `count`, `avg`, `sum`, `min`, `max`, `string_agg`                                                 |
| [Type conversion](functions/conversion.md)        | `to_date`, `to_timestamp`, `to_integer`, `to_decimal`, `to_text`, …                               |
| [Window functions](functions/window.md)           | `dense_rank`, `lag`, `lead`, `ntile`, `rank`, …                                                   |

## Per-dialect support


[DuckDB](dialects/duckdb.md)

[MariaDB](dialects/mariadb.md)

[MSSQL](dialects/mssql.md)

[Oracle](dialects/oracle.md)

[PostgreSQL](dialects/postgresql.md) 

[Snowflake](dialects/snowflake.md) 

[SQLite](dialects/sqlite.md)

[Trino](dialects/trino.md)
