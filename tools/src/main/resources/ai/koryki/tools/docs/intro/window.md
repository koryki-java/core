
Six functions that answer "where does this row sit among those rows": `row_number`, `rank`,
`dense_rank`, `ntile`, `lag` and `lead`. Unlike an aggregate, a window function leaves the rows
alone — every row keeps its identity and gains an answer about its neighbours.

## The OVER clause says which rows, and in what order

    rank() OVER (PARTITION c.category_name ORDER p.unit_price DESC)

`PARTITION` splits the rows into groups the function is computed within; `ORDER` fixes the sequence
inside each group. KQL writes both without `BY`, matching how the rest of the language reads.

**Ranking without an order is refused.** `rank()` with nothing to rank by would give an arbitrary
answer that changes between runs, so the query is rejected with a message saying so, rather than
answered. The one deliberate exception is `row_number`: numbering the rows of an unordered
partition is a legitimate thing to ask for.

Aggregates take an `OVER` clause too — `sum(...) OVER (...)` is a running total, and that is where
most window queries actually start. Two of them cannot on every engine; the *Aggregate Functions*
page says which.

## Frames narrow the window further

    avg(p.unit_price) OVER (PARTITION c.category_name ORDER p.unit_price
                            ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING)

Without a frame the function sees the whole partition. `ROWS BETWEEN` restricts it to a sliding
span around the current row — the example above is a three-row moving average. The bounds are
`UNBOUNDED PRECEDING`, `n PRECEDING`, `CURRENT ROW`, `n FOLLOWING` and `UNBOUNDED FOLLOWING`, and
`0 PRECEDING` is a real bound meaning the current row alone, not an absent one.

## lag and lead reach across rows

`lag(x)` is the value of *x* one row back in the ordering, `lead(x)` one row forward — the way to
compare a row with its predecessor without joining the table to itself. Both take an optional
offset, and the first (or last) row of each partition has no neighbour, so the result there is
blank unless you supply a default.

## The ordering has to be total, or the answer is not reproducible

This is the trap that survives all the syntax. If two rows tie on the `ORDER` expression, nothing
decides which comes first, and each engine — sometimes each run — may choose differently. A
`row_number` over a tied ordering is stable in appearance and arbitrary in fact. Order by something
unique, or add a tiebreaker column, whenever the numbering itself is the answer.
