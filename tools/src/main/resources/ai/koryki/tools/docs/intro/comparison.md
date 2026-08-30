
A comparison is a **predicate**: it yields true, false, or null, and it is what `FILTER` and
`HAVING` take. See the *Logical Operators* page for how predicates combine with `AND`, `OR` and
`NOT`, and [TEMPORAL](TEMPORAL.md) for the temporal comparison rules.

## NULL never compares equal

Null means *unknown*, so every comparison with a null operand yields null rather than true or
false — including `null = null`. A `FILTER` keeps only rows whose predicate is **true**, so a
row with a null operand is dropped by a comparison *and* by its negation:

    FIND orders o
    FILTER o.shipped_date < "1998-01-01"
    FETCH o.order_id

returns no unshipped order, and neither would the opposite comparison. This is why `ISNULL`
exists: it is the only way to test for null, since `= NULL` can never match.

    FIND orders o
    FILTER o.shipped_date ISNULL
    FETCH o.order_id

Its negation, "is not null", is written `NOT o.shipped_date ISNULL`.

### Comparing two values that may both be blank

`ISNULL` tests one value. To compare *two* columns that may each be empty, use `DISTINCT`, which
counts a missing value as a difference and two missing values as equal — so it always answers true
or false, never unknown:

    FIND customers c, orders o
    FILTER c.country = 'France' AND NOT c.region DISTINCT o.ship_region
    FETCH c.customer_id, c.region

This is the same distinctness `FETCH DISTINCT` applies when it collapses duplicate rows: there too,
two blanks count as the same value. So `a DISTINCT b` asks "are these two genuinely different
values", and `NOT a DISTINCT b` asks "are they the same, blanks included".

Here is an example, French customers have no region recorded, and neither do their orders. Written with `<>` the query
above finds no differences to exclude and `=` matches nothing, because comparing two missing values
is unknown rather than true. With `DISTINCT` the blanks compare as equal and the orders come back.

**Prefer `=` and `<>` when the columns cannot be empty.** A null-safe comparison is not a plain
equality test, so on some engines it can stop an index being used where `=` would use one; if you
are filtering a large table it is worth checking that on your own data before making `DISTINCT` a
habit.

## Ordered comparisons need an order

`<`, `<=`, `>`, `>=` and `BETWEEN` require operands with a total order. Numbers, text, dates,
timestamps and times all have one. Durations only have one **within a unit class** — clock
(`h m s ms`), days (`d`), or months (`mo q y`) — so ordering `1mo` against `30d`, or using a
mixed duration like `1d2h`, is rejected: there is no anchor-independent answer. A
string-encoded interval is not orderable at all. Compare those with `=` or `<>` instead, or
store the value as a numeric `INTERVAL:<unit>` (see [TEMPORAL](TEMPORAL.md)).

## Ranges over dates and timestamps

`value BETWEEN low AND high` is inclusive on both ends — but when *high* is a DATE or TIMESTAMP
**literal** it is rewritten in SQL to `value >= low AND value < high + 1d`. That is what makes a range
ending on a day include the whole day: a plain SQL `BETWEEN … AND '1996-12-31'` on a TIMESTAMP
column stops at `00:00:00` and silently drops almost all of the last day. The BETWEEN sample
below shows the rewrite in its generated SQL.

The rewrite only fires for a **literal** upper bound. With a computed one — `month_end(...)`,
say — no rewrite happens and the half-open form has to be written out by hand (see
[TEMPORAL](TEMPORAL.md)).

## Membership tests

    FILTER c.country IN ('USA', 'Canada')

    FILTER NOT c.customer_id IN (
        FIND orders o
        FETCH o.customer_id
    )

Beware the second form with `NOT`: if the subquery yields a single null, the membership test is
unknown for every row and the result is empty. `NOT EXISTS` is immune and is usually what you
want.

## Pattern matching with LIKE

`%` matches any run of characters, `_` exactly one. Both operands must be TEXT.

Whether `LIKE` is case-sensitive is **not** decided by KQL — it follows the column's collation,
so the same query can match differently on different databases (and on the same database with a
different collation). Where it matters, normalise explicitly with `upper()` or `lower()` on both
sides rather than relying on the server default. "Does not match" is written `NOT x LIKE '…'`.

## Custom operators

The grammar accepts any lowercase identifier as an infix operator, and one that is not in the
catalog is passed through to the generated SQL verbatim. That is a deliberate escape hatch for
dialect-specific operators, but it is unchecked: the query works on the database it was written
against and may fail on every other one. Transpiling reports it as a warning.
