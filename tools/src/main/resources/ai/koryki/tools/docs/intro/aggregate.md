
Seven functions that turn many rows into one: `count`, `count_distinct`, `sum`, `avg`, `min`, `max`
and `string_agg`. You never write `GROUP BY` — using one of these in a projection is what makes the
query grouped, and the columns you also fetch become the grouping key.

## count() and count(x) ask different questions

`count()` counts **rows**. `count(x)` counts the rows where *x* is not blank, which is how you ask
"how many orders actually have a shipping date". The two differ exactly by the blanks, and that is
usually the point of writing the second one.

    count()                 →  830
    count(o.shipped_date)   →  809   -- 21 orders never shipped

`count()` becomes `COUNT(*)`, not `count()`. Only DuckDB, SQLite and Trino accept the empty
parentheses; PostgreSQL, SQL Server, MariaDB, Oracle and Snowflake reject them outright with *"must
be used to call a parameterless aggregate function"*. That the reference dialect is one of the three
permissive ones is why it went unnoticed for a while.

`count_distinct(x)` is its own name rather than a modifier on `count`, because `DISTINCT` inside a
function call is SQL syntax that not every engine allows in every position — see the window note
below.

## Counting different rows takes the whole key

`count_distinct` also takes **several** values, and then counts how many different *combinations*
there are. That exists because a row is not always identified by one column — naming the entity
counts order **lines**:

    FIND order_details od FETCH count_distinct(od) n

| what you write | what it counts | |
|---|---|---|
| `count_distinct(od)` | order lines | 2155 |
| `count_distinct(od.order_id)` | orders | 830 |

An order line is identified by order **and** product together. Both are legitimate questions; they
are just different ones, and writing the entity asks the first.

Engines disagree about how to spell it, and koryki hides that:

| | rendered as |
|---|---|
| DuckDB, PostgreSQL, Trino | `COUNT(DISTINCT (a, b))` |
| MariaDB, Snowflake | `COUNT(DISTINCT a, b)` |
| Oracle, SQL Server, SQLite | refused — the engine has neither form |

Where it is refused, the message names the way out: concatenate the values yourself.

    FIND order_details od FETCH count_distinct(concat(od.order_id, '#', od.product_id)) n

**Choose that separator carefully, and do not omit it.** Without one, `(1, 23)` and `(12, 3)` both
become `123` and count as the same combination; with one that occurs in the data, the same collision
comes back for the rows that contain it. koryki does not pick the separator for you precisely
because no character can be guaranteed absent from your values — that decision has to be yours.

## sum keeps the type, avg does not

`sum`, `min` and `max` return whatever the argument was: a sum of integers is an integer, a sum of
money is money. `avg` always comes back fractional, because an average of whole numbers usually
isn't one.

That distinction is worth stating because an engine can disagree: Trino used to return the average
of a `DECIMAL(10,2)` column as `DECIMAL(10,2)` as well, so the mean of three prices came out
`11.92` where every other engine said `11.916667`. The average of money is not money, and it is
levelled now.

## string_agg needs its separator

    string_agg(o.ship_city, ', ')

There is no default — the separator is a required argument, because the engines disagree on what a
missing one means. Each spells the function differently (`GROUP_CONCAT`, `LISTAGG`,
`array_join(array_agg(…))`), which is invisible until you try to use it as a window function.

## Not every aggregate can be a window function

`string_agg` has no `OVER` form on MariaDB, SQL Server, Oracle, Snowflake or Trino, and
`count_distinct` has none on MariaDB, SQL Server, PostgreSQL or SQLite. DuckDB is the only engine
that allows both. Where the engine cannot, the query is refused before it runs, with a message
naming the function and pointing at it — not a driver error after the fact.
