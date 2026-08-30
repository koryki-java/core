
The five arithmetic operators work on **numbers, dates and durations** — not on text. `+` is
addition, never string concatenation; use `concat` for that. Anything that is not a number, a
date/time value or a duration is rejected when the query is checked, rather than being coerced into
whatever that particular database happens to do with it.

    FIND order_details od
    FETCH od.unit_price * od.quantity gross

## Precedence

`*` and `/` bind tighter than `+` and `-`, as in ordinary arithmetic, and parentheses override that:

    a + b * c        →  a + (b * c)
    (a + b) * c      →  grouped as written

The grouping you write is the grouping that reaches the database. Parentheses are preserved through
the whole pipeline, so a query cannot be quietly re-associated on the way to SQL.

## Division

**Division always produces a decimal**, including when both operands are whole numbers:

    7 / 2   →   3.5

This is worth stating because SQL itself does not agree with it. Left to their own devices,
PostgreSQL, SQL Server, Trino and SQLite answer `3` — integer division truncates — while Oracle,
MariaDB and DuckDB answer `3.5`. The same query would give two different numbers
depending on where it ran, so KQL settles it: the result is decimal everywhere. Use `floor` or `round` when you
want a whole number back.

**Dividing by zero gives a blank, not an error.** A row whose divisor is zero yields no value for
that expression, and the rest of the report is unaffected:

    FIND order_details od
    FETCH od.unit_price / od.quantity unit_cost

If `quantity` is zero for some line, `unit_cost` is blank for that line only. Here too the databases
disagree natively — four raise an error, two return a blank, one returns infinity — so KQL settles
on the blank. That means a divide-by-zero will not fail a report, but it will not announce itself
either: if a zero divisor means your data is wrong, filter for it explicitly (`FILTER od.quantity >
0`) rather than relying on the query to complain.

Writing `/ 0` literally is a different matter and is rejected outright, since it can never have been
intended.

## Dates and durations

Arithmetic on dates and times follows its own algebra, described in full in `docs/TEMPORAL.md`. The
short version:

    o.order_date + 30d           →  a date, 30 days later
    o.shipped_date - o.order_date →  a duration in whole days
    3 * 2h                        →  6h

Subtracting two dates gives **whole days**, and subtracting two timestamps gives an exact elapsed
span in clock units — never calendar months, whose length is not fixed. When the calendar breakdown
is what you want, that is `calendar_distance`, not `-`. Multiplying a duration by a whole number
scales each of its components; dividing a duration is not defined.

## Decimal places

How many decimal places come back is the database's decision, not KQL's: the same product may print
as `77`, `77.0` or `77.000000000000` depending on the engine's own scale rules. The value is the
same. Use `round(x, 2)` when the presentation matters.

## Not here

There is no `%` operator — the remainder is the `mod` function, on the *Mathematical Functions*
page, along with `abs`, `floor`, `ceil`, `round`, `power` and `sqrt`.
