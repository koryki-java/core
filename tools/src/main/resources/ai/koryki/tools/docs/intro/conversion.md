
Turning one type into another. There is a function per target type — `to_date`, `to_integer`,
`to_text` — rather than one `cast(value, type)`, because the target is not a value: it cannot come
from a column or be passed in, so it belongs in the name.

## The one that surprises people: integers round

    to_integer(1.7)     →   2
    to_integer(-1.7)    →  -2
    to_integer(2.5)     →   3

**Rounded, not cut off**, and half-way values go away from zero. `to_bigint` and `to_smallint`
behave the same. If a value must never be inflated — a fee, a quota, an invoice line — apply
`trunc` first and convert afterwards:

    to_integer(trunc(od.unit_price))

This is worth stating plainly because SQL itself does not agree with itself here: left alone,
SQLite and SQL Server cut the fraction off while every other engine rounds it. KQL levels that, so
the same query gives the same number everywhere.

## Text in, text out

`to_text` converts anything to text; the reverse direction is where it gets particular. `to_date`,
`to_time` and `to_timestamp` accept text only in ISO form — `"2023-01-31"`, `"14:30:00"`,
`"2023-01-31 14:30:00"`. Anything else is up to the database, which is a polite way of saying it may
work on one and fail on the next. For a text layout that is not ISO, use `parse_date`,
`parse_time` or `parse_timestamp` with an explicit mask; that is what they are for.

## Where you must say how wide

Two conversions take a size, and it is not pedantry:

    to_decimal(value, 10, 2)      10 significant digits, 2 after the point
    to_varchar(value, 40)         at most 40 characters

A decimal without a scale and a varchar without a length mean different things on different
databases — different default precision, different silent truncation. Asking is better than
guessing on the user's behalf.

## One caveat on booleans

`to_boolean` treats `0` as false and anything else as true, everywhere. What comes *back* differs:
databases with a native boolean type answer `true`/`false`, while MariaDB and SQLite have no such
type and answer `1`/`0`. The truth value agrees; only the notation does not. Compare the result
rather than matching it against the text `'true'`.
