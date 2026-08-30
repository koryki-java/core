
Two functions, and both are really about the mask: `to_char` turns a date or timestamp into text,
`to_number` reads a number back out of text.

## The datetime mask is written once

Unlike a regular-expression pattern, a format mask **is** translated. You write it in KQL's
vocabulary and each database receives its own spelling — strftime codes for DuckDB and SQLite,
`DATE_FORMAT` codes for MariaDB and Trino, date-part expressions for SQL Server. PostgreSQL and
Oracle need no rewriting because the vocabulary is theirs to begin with.

    YYYY  YY          year
    MM                month
    DD                day
    HH24  HH12  HH    hour — HH is the 12-hour clock
    MI    SS          minute, second
    AM    PM          meridiem

    to_char(o.order_date, 'YYYY-MM')        →  2024-07
    to_char(o.delivered_at, 'DD.MM.YYYY HH24:MI')

Tokens are matched exactly as written: `YYYY`, not `yyyy`. Text that should survive untouched goes
in double quotes — `'YYYY "week" WW'`.

## There are no month or weekday names

Deliberately. They were in the vocabulary once and gave five different answers for the same day:
`July` on three databases, `JULY` padded to nine characters on PostgreSQL, `JULI` on Oracle, `Juli`
on Trino — and an empty column on SQLite, whose strftime has no such code at all. Two of them
answered in the language of whoever ran the query, not in one the query chose.

A mask containing `MONTH`, `MON`, `DAY` or `DY` is now rejected with a message that says so. Use
`MM` and `DD` for the numbers and render the name where you know the language you are writing in —
which is the application, not the database.

## to_number is a different vocabulary, and not portable

`to_number` parses text into a number using a **numeric** template — `9` for a digit, `S` for a
sign — and that mask is *not* translated; it goes to the database as written. Only PostgreSQL,
Oracle and Snowflake offer the function at all; the other five declare it unsupported.

    to_number('  42', '9999')   →  42
    to_number('-17', 'S99')     →  -17

It earns its place where the text carries formatting a plain cast would choke on. If the text is
already a bare number, `to_integer` or `to_decimal` is the simpler and portable answer.
