---
menuTitle: "Window Functions"
parent: "FUNCTIONS"
order: 12
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Window Functions

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

## row_number

`row_number()` → BIGINT

Position of the row within its window, counting from 1. Ties are numbered arbitrarily — use `rank` if equal values should share a number.

Sample query:

```kql
// row_number: number each customer's orders from their earliest, without collapsing any rows.
FIND customers c, orders o
FILTER c.country = 'France'
FETCH c.customer_id, o.order_id, row_number() OVER (PARTITION c.customer_id ORDER o.order_id) nth
```

### Generated SQL

**all dialects**

```sql
-- row_number: number each customer's orders from their earliest, without collapsing any rows.
SELECT
  c.customer_id
, o.order_id
, row_number() OVER (PARTITION BY c.customer_id ORDER BY o.order_id) AS nth
FROM
 customers c
  INNER JOIN orders o ON
   c.customer_id = o.customer_id
WHERE
  c.country = 'France'
```


## rank

`rank()` → BIGINT

Position of the row by the window's ordering, where equal values share a rank and the next rank skips ahead: 1, 2, 2, 4.

Sample query:

```kql
// rank: position by freight, heaviest first. Equal freights share a rank and the next one skips.
FIND orders o
FILTER o.ship_country = 'France'
FETCH o.order_id, o.freight, rank() OVER (ORDER o.freight DESC) freight_rank
```

### Generated SQL

**all dialects**

```sql
-- rank: position by freight, heaviest first. Equal freights share a rank and the next one skips.
SELECT
  o.order_id
, o.freight
, rank() OVER ( ORDER BY o.freight DESC) AS freight_rank
FROM
 orders o
WHERE
  o.ship_country = 'France'
```


## dense_rank

`dense_rank()` → BIGINT

Like `rank`, but without gaps after a tie: 1, 2, 2, 3.

Sample query:

```kql
// dense_rank: like rank, but the numbering has no gaps after a tie.
FIND orders o
FILTER o.ship_country = 'France'
FETCH o.order_id, o.freight, dense_rank() OVER (ORDER o.freight DESC) freight_rank
```

### Generated SQL

**all dialects**

```sql
-- dense_rank: like rank, but the numbering has no gaps after a tie.
SELECT
  o.order_id
, o.freight
, dense_rank() OVER ( ORDER BY o.freight DESC) AS freight_rank
FROM
 orders o
WHERE
  o.ship_country = 'France'
```


## ntile

`ntile(buckets: numeric)` → BIGINT

Splits the window's rows into *buckets* groups of near-equal size and returns which group the row falls in — quartiles with `ntile(4)`.

| Argument | Type | Description |
|---|---|---|
| buckets | numeric | how many groups to divide the rows into |

Sample query:

```kql
// ntile: split the orders into four groups of near-equal size by freight — quartiles.
FIND orders o
FILTER o.ship_country = 'France'
FETCH o.order_id, o.freight, ntile(4) OVER (ORDER o.freight) quartile
```

### Generated SQL

**all dialects**

```sql
-- ntile: split the orders into four groups of near-equal size by freight — quartiles.
SELECT
  o.order_id
, o.freight
, ntile(4) OVER ( ORDER BY o.freight) AS quartile
FROM
 orders o
WHERE
  o.ship_country = 'France'
```


## lag

`lag(value: any [, offset: numeric] [, default: any])` → argument-dependent

The value from an earlier row of the window — the previous month's figure, say, for comparing against this one.

| Argument | Type | Description |
|---|---|---|
| value | any | the value to read from an earlier row |
| offset | numeric *(optional)* | how many rows back to look; 1 (the previous row) by default |
| default | any *(optional)* | value to use when there is no such row; null by default |

Sample query:

```kql
// lag: each order alongside the previous order's freight for the same customer — the shape behind
// "compare with the period before".
FIND customers c, orders o
FILTER c.country = 'France'
FETCH c.customer_id, o.order_id, o.freight, lag(o.freight) OVER (PARTITION c.customer_id ORDER o.order_id) previous_freight
```

### Generated SQL

**all dialects**

```sql
-- lag: each order alongside the previous order's freight for the same customer — the shape behind
-- "compare with the period before".
SELECT
  c.customer_id
, o.order_id
, o.freight
, lag(o.freight) OVER (PARTITION BY c.customer_id ORDER BY o.order_id) AS previous_freight
FROM
 customers c
  INNER JOIN orders o ON
   c.customer_id = o.customer_id
WHERE
  c.country = 'France'
```


## lead

`lead(value: any [, offset: numeric] [, default: any])` → argument-dependent

The value from a later row of the window — the mirror of `lag`.

| Argument | Type | Description |
|---|---|---|
| value | any | the value to read from a later row |
| offset | numeric *(optional)* | how many rows forward to look; 1 (the next row) by default |
| default | any *(optional)* | value to use when there is no such row; null by default |

Sample query:

```kql
// lead: the mirror of lag — the next order's freight for the same customer.
FIND customers c, orders o
FILTER c.country = 'France'
FETCH c.customer_id, o.order_id, o.freight, lead(o.freight) OVER (PARTITION c.customer_id ORDER o.order_id) next_freight
```

### Generated SQL

**all dialects**

```sql
-- lead: the mirror of lag — the next order's freight for the same customer.
SELECT
  c.customer_id
, o.order_id
, o.freight
, lead(o.freight) OVER (PARTITION BY c.customer_id ORDER BY o.order_id) AS next_freight
FROM
 customers c
  INNER JOIN orders o ON
   c.customer_id = o.customer_id
WHERE
  c.country = 'France'
```

