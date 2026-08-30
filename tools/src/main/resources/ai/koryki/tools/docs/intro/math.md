
The everyday numeric toolkit: rounding, roots and powers, logarithms, and the row-wise `greatest` /
`least`. Operators (`+`, `-`, `*`, `/`) live on the *Arithmetic Operators* page; everything here is
written as a function call, deliberately — `mod(x, 2)` reads better than `x % 2` for an audience that
reads `%` as "percent".

## Rounding: round, floor, ceil, trunc

Four ways to drop decimals, and the difference matters:

    round(19.99)      →  20     nearest
    floor(19.99)      →  19     always down
    ceil(19.01)       →  20     always up
    trunc(19.99)      →  19     always toward zero

`floor` and `trunc` agree on positive numbers and part company on negatives: `floor(-1.5)` is `-2`
(further down), `trunc(-1.5)` is `-1` (toward zero). Use `trunc` when a value must never be inflated
— a fee, a quota — and `round` when it should simply be nearest.

`round` and `trunc` both take an optional number of decimal places: `round(x, 2)`, `trunc(x, 2)`.

**One caveat, unavoidable.** Half-way rounding of values held as binary floats is decided by the
database, not by KQL: `0.15` is stored as `0.149999…`, so an engine may round it either way. Where
that matters, keep the value a decimal rather than a float.

## Logarithms: ln, log10, log

There are three, and the naming is deliberate:

    ln(x)          natural logarithm, base e
    log10(x)       base 10
    log(base, x)   any base — log(2, 8) is 3

**There is no one-argument `log`,** and that is not an omission. Every engine accepts `log(100)` and
they do not agree what it means: PostgreSQL and SQLite read it as base 10 and return `2`, MariaDB and
SQL Server read it as the natural log and return `4.605`. A name that means two things cannot be
fixed by translating it, only by not offering it — so say which one you want, and `ln` or `log10`
will mean the same thing on every database.

With two arguments the **base comes first**, as it reads aloud: "log base 2 of 8".

## greatest and least

These compare **across the columns of one row** — not down a column:

    FIND order_details od
    FETCH greatest(od.unit_price, 20.0) at_least_twenty

That is the row's own price, or 20 if the price is lower. The functions that collapse many rows into
one are `max` and `min`, on the *Aggregate Functions* page. It is an easy pair to confuse, and the
two answer completely different questions.

**One caveat, and this one is not cosmetic.** As soon as an argument can be blank, the engines part
company. With `greatest(NULL, 3)`, PostgreSQL, DuckDB and SQL Server answer `3` — they ignore blanks and take the
largest of the rest — while MariaDB, Oracle, Snowflake, SQLite and Trino answer blank, because the SQL
standard lets a blank win the moment it appears. Both camps are defensible; PostgreSQL documents its
deviation openly.

KQL does not paper over it. Levelling it would mean rewriting one camp's SQL, and with a variable
number of arguments that needs a replacement construct rather than a translation — a lot of
machinery to hide a difference the query itself can state. So say which you mean: wrap each argument
in `coalesce` with a neutral value to ignore blanks, or keep the blanks in the call and let them win
everywhere. If the arguments cannot be blank, none of this applies and the functions are portable.

## Division and remainders

`mod(x, y)` is the remainder. Like `/`, **a zero divisor yields a blank rather than an error** — so a
report will not fail on one bad row. This is worth knowing because SQL itself is wildly inconsistent
here: left alone, three of the supported engines raise an error, three return a blank, and Oracle
returns the dividend unchanged, which looks like a real answer and is not one.

## Trigonometry

`sin`, `cos`, `tan`, `asin`, `acos` and `atan` are present and take **radians**. They are not the
common case for this audience, and the wider set — hyperbolics, `cot`, `atan2`, `degrees`/`radians`
— is deliberately not included; see `math-review.md` for the measurements behind that.
