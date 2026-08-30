
The largest category, and the one where the databases agree least. KQL works with four temporal
kinds — `DATE`, `TIME`, `TIMESTAMP` and `INSTANT` — described in `docs/TEMPORAL.md`; this page is
about the functions over them.

## Where the engines disagreed, the answer is pinned

Most functions here render straight through. Two do not, and they are worth knowing about:

`dayofweek` counts **Monday as 1** and Sunday as 7, the ISO way. Left alone, the engines gave three
different numbers for the same Sunday — 0, 1 and 7 — and several took the answer from a session
setting (`NLS_TERRITORY`, `DATEFIRST`, `WEEK_START`), so the same query could differ between two
connections to the *same* database. `week` is ISO-8601 for the same reason: week 1 is the one
containing the first Thursday, which is why early January sometimes falls in week 52 or 53 of the
previous year.

Where an engine's own answer depends on a session setting, the dialect computes it from a fixed
anchor instead of asking the session.

## Prefer the named boundaries to date_trunc

`day_begin`, `week_begin`, `month_begin`, `quarter_begin`, `year_begin` and their `_end` partners
are the portable way to snap a value to a period. `date_trunc(part, value)` does the same for
`'day'`, `'month'`, `'quarter'` and `'year'` — and only those four, because that is the set every
dialect can express. Ask for `'hour'` and SQLite used to answer with a blank column rather than an
error; it now fails by name.

The `_end` functions return the last **day** of the period, always a date. That is deliberate:
"the last instant" has no value independent of precision. For a timestamp range, filter half-open
with `begin` and the next `begin`.

## year_month is the month as a key, month_begin is the month as a date

Two ways to bucket by month, and they are not interchangeable. `year_month` packs the two parts
into one number — `202401` — which sorts chronologically by itself, so a `GROUP year_month(...)`
needs no second column and no tie-break. `month_begin` answers with the first of the month, a real
date: use that one when the bucket has to join to a calendar table or take further date arithmetic.

What you should not do is group by `year` and `month` side by side. It needs two columns, and it
sorts wrong the moment one of them is left out of the ordering.

## Distances count whole units, toward zero

`days_between`, `months_between` and `years_between` follow `java.time`'s `ChronoUnit`: complete
units only, and the remainder is dropped in the direction of the sign.

    months_between('2023-01-31', '2023-03-30')  →  1    -- the second month is not complete

## Durations are literals, not function calls

    2d4h        two days and four hours
    1y2mo       one year and two months
    -2d4h       *both* components negated — 52 hours back, not 2 days back and 4 hours forward

Units are `ms s min h d w mo q y`, and they must be written largest-first (`1y2mo15d`, not
`15d2mo1y`); the other order is a positioned error rather than a silent reordering. A leading minus
applies to the whole literal, so mixed signs cannot be written — that is the one shape the notation
does not have.

Oracle is the exception worth naming: it keeps `YEAR TO MONTH` and `DAY TO SECOND` in separate
types, so a duration mixing the two (`1y2mo3d`) has no value there. Adding it to a date works —
that expands into steps — but using it *as* a value does not.

## now, clock_now and today

`now` is the statement's timestamp and does not move within it. `clock_now` reads the wall clock at
the moment of the call, so it may advance mid-statement on the engines that distinguish the two
(PostgreSQL `clock_timestamp()`, MariaDB `SYSDATE()`); elsewhere the two are equal. `today` is the
current date.

## Parsing, formatting and zones

`parse_date`, `parse_time` and `parse_timestamp` read text with a format mask, `to_char` writes it.
The mask is written once in KQL's vocabulary and translated per dialect — the tokens are on the
*Data Type Formatting Functions* page. SQLite has no mask-based parsing at all and SQL Server takes
culture or style codes rather than masks, so both declare the three unsupported.

`at_zone` and `to_utc` cross named time zones. SQLite has no time-zone database and declares both
unsupported; the other seven support them.
