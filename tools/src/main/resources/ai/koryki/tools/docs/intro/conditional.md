
Three functions, and between them they cover what every other database spells with half a dozen
names. `coalesce` fills in a blank, `nullif` creates one, and `case` decides between values.

## Why there is no `if`

Every database offers a ternary — `if`, `iff`, `iif`, `nvl2`, `decode` — and no two agree on the
name, the argument order, or how many arguments there are. `case` says the same thing and says it
once:

    case(o.freight > 100, 'high', 'low')

The conditions are tested in order and the first one that holds wins. A trailing argument without a
condition is the fallback; leave it off and a row that matches nothing simply comes out blank. That
is one name to learn instead of five, and it means the same thing on every database.

## coalesce and nullif are opposites

    coalesce(c.region, c.country)     the first value that is not blank
    nullif(p.reorder_level, 0)        blank, when the value is that placeholder

`coalesce` is how you stop a blank from reaching a report. `nullif` is how you get one *in* —
because data is full of placeholders that mean "unknown" without being blank: a `0` that stands for
"no threshold set", an empty string that was never filled. Written together they clean a column in
one pass: `coalesce(nullif(c.region, ''), c.country)`.

**Only what is needed gets evaluated.** `coalesce` reads left to right and stops at the first
non-blank argument; `case` stops at the first condition that holds. Nothing to the right of that is
evaluated, which is why `coalesce(x, 1/0)` is safe. This is a guarantee, not an optimisation that
might not happen.

## One type for the whole column

A `case` produces one column, so all its results must be able to meet in a single type — decided
once for the expression, not per row. Branches that return text in one arm and a number in another
are refused outright, rather than yielding a column whose type depends on the data.

The same rule reaches `nullif`, for a less obvious reason: its two arguments are *compared*, so they
too must meet in one type — and the result then carries that common type rather than the first
argument's own. `nullif(quantity, 0.5)` comes out decimal, not integer.
