---
menuTitle: "Temporal Types"
parent: "REFERENCE"
order: 3
---

# Temporal Types

This document is **normative**: it defines what KQL promises about temporal
types, independent of any SQL dialect. Dialects implement these semantics ; 
where a dialect cannot, the function
or operation is rejected at validation time — never silently approximated.

The design goal familiarity for non-SQL experts: arithmetic just works.

## Types

| KQL type    | meaning                                   | literal                |
|-------------|-------------------------------------------|------------------------|
| `DATE`      | calendar day, no time, no zone            | `"2024-01-31"`         |
| `TIME`      | time of day, no date, no zone             | `"14:30:00"`           |
| `TIMESTAMP` | calendar day + time of day, no zone       | `"2024-01-31 14:30:00"`|
| `DURATION`  | amount of time, as a list of components   | `2d4h`, `1y2mo15d`     |

`DURATION` is the user-facing name of the internal `INTERVAL` type family.
A duration is an ordered list of `(value, unit)` components and is **never
normalized across the calendar/clock boundary**: `1mo30d` stays one month
and thirty days; it is not 2 months and not 61 days.

Units fall into two classes:

* **calendar units** — `y` (year), `q` (quarter), `mo` (month), `d` (day):
  variable length
* **clock units** — `h` (hour), `m` (minute), `s` (second), `ms`: fixed length

The day is a **calendar** unit: a civil day is 23 or 25 hours across a
daylight-saving transition, so its length is not fixed. Treating it as calendar
yields one rule users can rely on everywhere:

> **Duration arithmetic never changes a smaller digit unexpectedly.**

`+ 1d` keeps the time of day, `+ 1mo` keeps the day of month (modulo the
end-of-month clamping below). 

## Result-type algebra

Arithmetic on temporal values is closed under the following table. Any
combination not listed is a **validation error** .

### Addition / subtraction

| left        | op | right       | result      | rule                                                                                                                                                                           |
|-------------|----|-------------|-------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DATE`      | ±  | `DURATION`  | `DATE`      | if the duration is calendar-only (`d mo q y`)                                                                                                                                  |
| `DATE`      | ±  | `DURATION`  | `TIMESTAMP` | if the duration has any clock component (`h m s ms`)                                                                                                                           |
| `TIMESTAMP` | ±  | `DURATION`  | `TIMESTAMP` |                                                                                                                                                                                |
| `TIME`      | ±  | `DURATION`  | `TIME`      | wraps around midnight (mod 24h); duration must be clock units (`h m s ms`); `d` and calendar units are a validation error                                                      |
| `DATE`      | +  | `TIME`      | `TIMESTAMP` | composition: day + time of day                                                                                                                                                 |
| `DATE`      | −  | `DATE`      | `DURATION`  | signed, in whole days (see *Distances and Boundaries*)                                                                                                                         |
| `TIMESTAMP` | −  | `TIMESTAMP` | `DURATION`  | signed **exact elapsed span** in clock units (`h m s ms`), never calendar `d mo q y` — a 50-hour span is `50h`, not `2d2h`; for the calendar breakdown use `calendar_distance` |
| `TIME`      | −  | `TIME`      | `DURATION`  | signed, in `h m s`; may be negative — it does **not** wrap                                                                                                                     |
| `DURATION`  | ±  | `DURATION`  | `DURATION`  | component-wise merge, no cross-class normalization                                                                                                                             |

These rows are not commutative: `DATE`, `TIMESTAMP`, and `TIME` always occupy the **anchor** position on the left, and `DURATION` shifts the value.

### Multiplication

| left       | op | right     | result     | rule                          |
|------------|----|-----------|------------|-------------------------------|
| `DURATION` | ×  | integer   | `DURATION` | component-wise                |

Division of durations and multiplication by non-integers are not defined
(validation error).

### Bare numbers

A bare number never combines with a temporal value: `order_date + 30` is a
validation error ("30 what?"), because the fix is effortless and unambiguous
in KQL: `order_date + 30d`.

### Comparisons (`=`, `<`, `BETWEEN`, …)

| left        | right       | rule                                                                                                                                                                                                                                                                |
|-------------|-------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DATE`      | `DATE`      | calendar order                                                                                                                                                                                                                                                      |
| `DATE`      | `TIMESTAMP` | the `DATE` is promoted to midnight (`00:00:00`)                                                                                                                                                                                                                     |
| `TIMESTAMP` | `TIMESTAMP` | chronological order                                                                                                                                                                                                                                                 |
| `TIME`      | `TIME`      | clock order (`23:59` > `00:01`; no wrap awareness)                                                                                                                                                                                                                  |
| `DURATION`  | `DURATION`  | only if both reduce to the **same unit class**: both clock (`h m s ms`, as elapsed time), both days (`d`), or both months (`mo q y`). Cross-class (`1mo` vs `30d`, `1d` vs `25h`) or mixed durations (`1d2h`) have no anchor-independent order — a validation error |

The restriction above applies only to durations koryki itself controls — duration literals and numeric
`INTERVAL:<unit>` columns. A **native interval column** carries the database's own total order, which
koryki does not override: a comparison involving a native interval is left to the dialect and is
therefore **dialect-defined, not portable**. This is deliberate — a user who already relies on the
database's interval ordering should not find it silently rejected. See *DuckDB → INTERVAL* in the appendix.

### NULL

All temporal operations propagate NULL like every other SQL expression:
any NULL operand yields NULL.

## Distances and Boundaries

**DATE - DATE** differences come out in **whole days** — the smallest calendar
unit — **never in months or years**:

    2023-03-01 - 2023-02-01  =  28d
    2024-03-01 - 2024-02-01  =  29d      (leap year)

The result is never `1mo`: a month has no fixed length, so `1mo` would name
two different amounts of time in these two lines. The day-*count* between two
calendar dates is exact and unambiguous — a day's variable *length* only
matters for zoned storage (below), not for counting dates. When the month- or
year-difference itself is the question, that is a dedicated function —
not subtraction.

The same split governs the **difference between two timestamps**, which has two
legitimate meanings KQL keeps apart. `TIMESTAMP − TIMESTAMP` (the `−` operator)
is the **exact elapsed span**: fixed, in clock units, independent of any anchor
and of how each operand is stored, and never expressed in calendar `d mo q y`
(`50h`, not `2d2h`) — for the same reason `DATE − DATE` is never months. When the
**calendar breakdown** is the question — years, months and days plus a clock
remainder, *anchored on the start* and end-of-month clamped — that is the
dedicated function `calendar_distance(start, end)`, the inverse of duration
addition (`start + calendar_distance(start, end) == end`). A month is 28–31 days,
so the same span from a different start can decompose differently; that anchor
dependence is precisely why it is a function and not the `−` operator.

`calendar_distance` is **projection-only**: a mixed interval (months + days +
clock) has no anchor-independent order, so the result can only be *presented*,
never compared, ordered, grouped or fed to another function. Using it outside a
`FETCH` output is a positioned validation error — use `TIMESTAMP − TIMESTAMP`
for an elapsed span you can filter or sort.

    calendar_distance("2024-06-01 09:00:00", "2024-06-03 11:00:00")  =  2d2h          (the 50h span above, on the calendar)
    calendar_distance("2025-01-01 00:00:00", "2026-05-15 12:00:00")  =  1y4mo14d12h

Four function families make distances and boundaries explicit. All of them
are ordinary catalog functions — validated, documented and rendered per
dialect.

| family   | functions                                                                                                      | answers                                      |
|----------|----------------------------------------------------------------------------------------------------------------|----------------------------------------------|
| distance | `days_between(start, end)`, `months_between(start, end)`, `years_between(start, end)`                         | how many whole units lie between?            |
| span     | `calendar_distance`                                                                                            | how far apart, as one `y mo d` + clock span? |
| add      | `day_add`, `month_add`, `year_add`                                                                             | what is *n* units later / earlier?           |
| boundary | begin: `day_begin`, `month_begin`, `quarter_begin`, `year_begin` — end: `month_end`, `quarter_end`, `year_end` | where does the unit begin / end?             |

### Add: arithmetic with computed amounts

A duration literal (`3d`) can only express a constant. The `x_add` functions
take any expression as the amount, with exactly the clamping rules of
*Applying a duration*:

    day_add(o.order_date, o.processing_days)
    month_add("2023-01-31", 1)                  =  2023-02-28    (clamped)

### Unit boundaries: begin / end

`x_begin(value)` snaps a value down to the start of its unit — the dialect-portable
replacement for `date_trunc`. It is **type-preserving**: a `DATE` stays a `DATE`,
a `TIMESTAMP` stays a `TIMESTAMP` (time becomes `00:00:00`):

    month_begin("2024-03-17")     =  2024-03-01
    quarter_begin("2024-05-20")   =  2024-04-01
    year_begin("2024-05-20")      =  2024-01-01

`x_end(value)` returns the last **day** of the unit and **always returns a `DATE`**.
"End of a unit" as a timestamp has no precision-independent value (`23:59:59`? `.999`?),
so `end` answers the business question ("which day?") rather than committing to a last instant.

    month_end("2024-03-17")     =  2024-03-31
    month_end("2024-02-10")     =  2024-02-29    (leap year)
    quarter_end("2024-05-20")   =  2024-06-30
    year_end("2024-05-20")      =  2024-12-31

**Note the deliberate asymmetry**: `begin` preserves the input type, but `end`                                                                                                                                                                                                                           
always returns a `DATE`. "End of a unit" as a timestamp has no                                                                                                                                                                                                                                           
precision-independent value (`23:59:59`? `.999`? depends on the column), so                                                                                                                                                                                                                              
`end` answers the business question ("which day?") rather than picking a last                                                                                                                                                                                                                            
instant. The names pair, the return types do not — and that is on purpose.

Because `end` always returns a `DATE`, `ts <= month_end(...)` is a trap: the `DATE` promotes
to midnight, cutting off everything after `00:00:00` on the last day. Use a half-open interval:

    FILTER o.ts >= month_begin("2024-03-17") AND o.ts < month_add(month_begin("2024-03-17"), 1)

`BETWEEN` with a literal upper bound is automatically rewritten to the same half-open form —
`expr BETWEEN lower AND upper` becomes `expr >= lower AND expr < (upper + 1d)` — so literal
ranges are always safe. When the upper bound is a function call like `month_end(...)`, no
rewrite fires and the half-open pattern above must be written explicitly.

    FILTER o.order_date BETWEEN "2024-01-01" AND "2024-01-31"

rewrites to: 

    FILTER o.order_date >= "2024-01-01" AND o.order_date < "2024-02-01"

## Applying a duration

A duration is applied to a date, timestamp or time **component-wise, one
whole component at a time, largest unit first** (`y q mo d h m s ms`).

### Clamping

Calendar steps clamp to the end of the month when the target day does not
exist:

    2023-01-31 + 1mo  =  2023-02-28      (clamped)
    2024-01-31 + 1mo  =  2024-02-29      (leap year)
    2023-01-31 + 2mo  =  2023-03-31      (no clamping needed)

Components apply **whole**, not unit by unit: `+ 2mo` is one step of two
months, which is why `2023-01-31 + 2mo` lands on March 31 while applying
`+ 1mo` twice would clamp at February and land on March 28. A duration of
`2mo` and a duration of `1mo` applied twice are different operations.

### Order matters — and is therefore fixed

Because clamping can occur at each step, the application order changes the
result. KQL fixes the order (largest unit first) so every expression has
exactly one answer:

    2023-03-30 + 1mo1d

largest first:  2023-03-30 + 1mo = 2023-04-30,  + 1d = 2023-05-01

Applying the day first would give `2023-03-31 + 1mo = 2023-04-30` — a
different result.

### Subtraction

Subtraction applies each component negated, in the same largest-first order.
Because of clamping, subtraction is **not the inverse of addition**:

    2023-01-31 + 1mo  =  2023-02-28
    2023-02-28 - 1mo  =  2023-01-28      (not 2023-01-31)

This is inherent to calendar arithmetic.

### TIME

A duration applied to a `TIME` wraps around midnight:

    22:00:00 + 4h  =  02:00:00

Only clock units (`h m s ms`) are allowed on `TIME` (see the algebra);
`d` and calendar units are validation errors — under wrapping, whole days
would be silent no-ops, and a silent no-op is worse than an error.

### No hidden normalization

The same rule as everywhere else in this document: components are applied
exactly as written. `1mo30d` performs a one-month step and then a thirty-day
step; it is never rewritten to `2mo`, `61d`, or anything else.

## Time zones

All KQL temporal values are **wall-clock values without a zone**. The algebra
above is zone-free by design: `+ 1d` means one **calendar** day — the same
wall-clock time on the next date — not a fixed 24-hour span. Adding a day never
changes the time-of-day digits; that is the deliberate promise, and
the next section is where the variable-length day earns it.

Time zones are handled at the **storage boundary**, not in the language:

* Every temporal column carries one of three **storage declarations**
  (type encodings, the same mechanism as the TIME encodings). UTC storage is
  *not* required — declarations state what is actually in the column:

  | declaration        | meaning                                              | boundary conversion        |
  |--------------------|------------------------------------------------------|----------------------------|
  | *naive* (default)  | wall-clock in the model zone                         | none                       |
  | *instant*          | an absolute instant (`timestamptz` or epoch integer) | instant → model zone       |
  | *wall-clock(zone)* | wall-clock in a declared named zone                  | declared zone → model zone |

  The **model zone** (default UTC) is configurable per model. The algebra is
  identical in all cases: by the time an expression is evaluated, every value
  is a model-zone wall-clock value — columns with different storage zones are
  therefore directly comparable.

  Spelled as type encodings: *naive* is the **absence** of an encoding,
  *instant* is `INSTANT` or `EPOCH:<unit>`, and *wall-clock(zone)* is
  `DATE_WALLCLOCK:<zone>` or `TIMESTAMP_WALLCLOCK:<zone>` — each encoding binds to
  exactly one family, which the schema parser checks against the column's
  declared family.

  *instant* has **two physical storage forms** of the one declaration: a
  `timestamptz`-style column (`INSTANT`) and an integer count since 1970
  (`EPOCH:<unit>`, e.g. seconds or milliseconds). They are decoded identically —
  both yield the same model-zone wall-clock value — so the same instant stored
  either way compares and subtracts as equal; the choice is storage, not meaning.

  > **Implementation status.** *naive* and *instant* are wired: an `INSTANT` (and its `EPOCH:<unit>`
  > storage form) is read-normalized to a model-zone wall-clock value at the boundary. Still **not
  > wired**: *wall-clock(zone)* (`DATE_WALLCLOCK` / `TIMESTAMP_WALLCLOCK`) is parsed and family-checked
  > but its declared-zone → model-zone conversion is an inert marker. It must be done **SQL-side** (so the
  > column is in the model zone *before* arithmetic): a wall-clock column is stored naive/local, so doing
  > the conversion only at the read boundary would convert a bare column but not `col + 1h` (computed in
  > SQL on the raw local value), and across a DST transition add-then-convert ≠ convert-then-add for clock
  > units. The SQL-side conversion is **wired** for DuckDB and PostgreSQL (`AT TIME ZONE` two-step),
  > MariaDB, Oracle (`FROM_TZ … AT TIME ZONE`, DATE truncated), Trino (`with_timezone …`), and Snowflake
  > (`CONVERT_TIMEZONE`) — applied at every column reference so bare output and arithmetic stay consistent
  > and DST-correct. A dialect without named-zone conversion **rejects** wall-clock(zone) (the default
  > hook): SQLite. SQL Server uses `AT TIME ZONE` with Windows zone names, which needs CLR enabled on the
  > instance. Oracle, DuckDB, MariaDB and SQLite specifics are collected in the appendix. Engines disagree on the DST-ambiguous overlap hour, so that
  > case is not portable across dialects. The literal-side conversion is **wired for `EPOCH:<unit>` /
  > `DATE_FROM_EPOCH_DAY` columns** (literal rendered as an epoch count in the model zone), and for an
  > `INSTANT` (`timestamptz`) column the literal is reconciled to its absolute instant and rendered as each
  > dialect's instant literal (a `DATETIMEOFFSET` literal on SQL Server, a correctly-formatted `TIMESTAMP`
  > on Trino), so the comparison no longer relies on implicit string coercion. The explicit `at_zone()` /
  > `to_utc()` functions are **wired** too (same per-engine shift and the same SQLite/SQL Server limits).
* For comparisons against literals, the conversion is applied to the
  **literal, not the column** (computed at transpile time via `java.time`),
  so filters stay index-friendly. SQL-side conversion is only needed for
  column-to-column comparisons across zones and for output.
* Wall-clock storage inherits the inherent DST ambiguity (one nonexistent and
  one doubled hour per year); resolution is delegated to the conversion
  mechanism and pinned by the conformance tests. Dialects without named-zone
  conversion reject *wall-clock(zone)* columns at validation time.
* The engine pins the session time zone on every connection. `now()` and
  zone-aware reads are reproducible across clients.
* Crossing zones is always **explicit**: `at_zone(ts, 'Europe/Berlin')`
  converts an instant to a wall-clock value in a named zone (this is the
  function to use before day-bucketing: `date(at_zone(o.ts, 'Europe/Berlin'))`
  is "the order day in Berlin"). `to_utc(...)` is the inverse. There is no
  implicit conversion anywhere.

### Daylight saving time: where the 23- and 25-hour days went

In zones that observe DST, one calendar day per year has 23 wall-clock hours
and one has 25. The variable-length day is exactly what lets KQL handle them
without surprising the user:

* `d` is a **calendar** unit: `+ 1d` means "the same wall-clock time on the
  next date" (`java.time` `plusDays`), rendered as a calendar interval — **not**
  a fixed 24-hour span. So `+ 1d` and `+ 24h` are different operations. They
  coincide on a naive value, but on a zone-normalized column (an *instant* or
  *wall-clock(zone)* storage, below) in a DST model zone, `+ 1d` preserves the
  time of day while `+ 24h` shifts it by the transition. A fixed-length day
  would silently change the clock digits across those two nights a year; the
  calendar day never does.
* DST enters only at the storage boundary. When *instant* or
  *wall-clock(zone)* columns are normalized into a model zone that observes
  DST, the converted values live on a timeline with one skipped and one
  doubled hour per year. A difference between two such values is a
  **wall-clock difference**: across a transition it deviates from physically
  elapsed time by the DST shift — 24 elapsed hours read as 25 wall-clock
  hours across the spring-forward night, and as 23 across fall-back.

Which behavior you get is the model-zone setting, chosen per model:

* model zone **UTC** (the default) or any fixed-offset zone: no DST anywhere
  — differences are exact elapsed time, day-bucketing follows UTC days.
* model zone = a **business zone** (e.g. `Europe/Berlin`): day-bucketing
  matches the business calendar — differences spanning the two transition
  nights per year are wall-clock, not elapsed time.

If exact physical elapsed time across DST transitions matters in a
business-zone model, that is an explicit function on instant-encoded columns
(elapsed seconds between two instants) — deliberately not expressible in the
wall-clock algebra.

### Presentation and localization are a separate layer

Localized rendering — date/time formats, percentages, currencies — happens
when **reading JDBC results**, after SQL execution. It is not part of KQL or
of SQL generation, and it must stay that way:

* **Formatting never changes a value.** `31.01.2024` and `2024-01-31` are the
  same DATE; a locale must not silently convert zones or shift day
  boundaries. Anything that changes the value (zone conversion, rounding) is
  semantics and belongs in the query, where it is visible and testable.
* Because zone and locale are both kept out of the algebra, the generated SQL
  is identical for every user; only the presentation of the result set
  differs. That keeps transpilation deterministic and tests meaningful.

### Prerequisite: catalog and schema quality

This whole approach stands on the schema description being right. The
encoding of a column (instant vs. wall-clock timestamp, the TIME encodings) 
is **declared metadata** — KQL trusts it and
cannot detect a wrong declaration. A `timestamptz` column described as a
naive `TIMESTAMP` silently shifts values by the session offset; that failure
mode is the reason this section exists. Treat the type descriptors in the
schema as carefully as the data itself: they are the contract that makes
zone-free semantics safe.

# Appendix: dialect implementation notes

## ORACLE

### INTERVAL

Oracle has no single interval type. It splits the two unit classes into separate
types — `INTERVAL YEAR TO MONTH` and `INTERVAL DAY TO SECOND` — each with its own
literal form:

    duration   INTERVAL YEAR TO MONTH     -- INTERVAL '2-6' YEAR TO MONTH        (2y 6mo)
    break_time INTERVAL DAY TO SECOND      -- INTERVAL '2 05:00:00' DAY TO SECOND (2d 5h)

### TIME

Oracle has no `TIME` type; use `DATE` or `TIMESTAMP`.

## DUCKDB

### INTERVAL

DuckDB has a **single** native `INTERVAL` type. It stores three independent
fields — **months, days, microseconds** — and, like a KQL `DURATION`, never
normalizes across them: `INTERVAL '1 month 30 days'` stays one month and thirty
days, not two months and not sixty-one days. The three fields line up with the
KQL unit classes plus the standalone day:

* month-family units (`y q mo`) read only the **months** field
* the `day` unit reads only the **days** field — a day is **not** 86 400 s
* clock units (`h m s ms`) reduce from **microseconds**

The DuckDB JDBC driver has no interval value class, so any INTERVAL — a native
interval column or a bare duration expression — reads back as DuckDB's verbose
string (`1 year 2 months 3 days 04:05:06.789`, `4 days 05:06:07`, `00:01:30`),
which koryki parses to the canonical `(value, unit)` list.

#### Cross-class ordering is dialect-defined

koryki refuses an anchor-independent ordering of its **own** durations across
unit classes (`1mo` vs `30d`, `1d` vs `25h`) — see *Comparisons* in the main
document. It does **not** impose that rule on a **native** `INTERVAL` column:
DuckDB carries its own total order over intervals (a 30-day month, a 24-hour
day), so a cross-class comparison against a native interval is left to the
engine and is therefore **dialect-defined, not portable**.

## MARIADB

### INSTANT — zone conversion

MariaDB has no `timestamptz`. An *instant* column is a plain `TIMESTAMP`, which
MariaDB stores as UTC and converts to the **session** time zone on every read
and write. koryki pins the session zone on each connection with a **numeric
offset** (`SET time_zone = '+00:00'`) rather than a named zone — that works
without the server's `mysql.time_zone*` tables being loaded and keeps instant
reads reproducible. Because the value the driver returns is therefore already
the model-zone wall-clock value, the `readInstant` hook reads it as a **naive
model-zone** timestamp and applies no further shift. (The base
`getObject(OffsetDateTime.class)` would re-stamp that wall-clock with the JVM
offset and move the instant — hence the override.)

#### Named-zone (wall-clock) conversion

Declared-zone → model-zone conversion for a `*_WALLCLOCK` column is done
SQL-side with `CONVERT_TZ(col, '<declared>', '<model>')` at every column
reference (a `DATE` column is `CAST` to `DATETIME` for the call and back to
`DATE` afterwards). `CONVERT_TZ` with **named** zones requires the server's
time-zone tables (`mysql.time_zone*`) to be populated; the numeric-offset
session pinning above does not.

## SQLITE

### No time zones

SQLite has no date/time types and **no time-zone database**. The consequences
run through the whole temporal layer:

* *wall-clock(zone)* storage (`DATE_WALLCLOCK` / `TIMESTAMP_WALLCLOCK`) is
  **rejected at validation time** — there is no named-zone conversion to apply.
  The explicit `at_zone()` / `to_utc()` functions are rejected for the same
  reason. Wall-clock fixtures therefore `ignore` SQLite.
* *instant* columns come back as ISO-8601 **text** carrying an explicit UTC
  suffix (`2024-01-31 14:30:00+00`); the `readInstant` hook normalizes that to
  an absolute `Instant`.
* temporal **literals** are emitted as plain quoted strings (`'2024-01-31'`,
  `'2024-01-31 14:30:00'`) — SQLite has no ANSI typed literals (`DATE '…'`).
* duration arithmetic uses SQLite's `date()` / `datetime()` modifiers — with
  explicit **end-of-month clamping** for month steps, since `'+N months'`
  overflows (`2025-12-31 +2mo` → `2027-03-03`) — rather than a native
  `INTERVAL` type.