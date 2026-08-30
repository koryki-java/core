---
menuTitle: "Conditional Expressions"
parent: "FUNCTIONS"
order: 9
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Conditional Expressions

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

## coalesce

`coalesce(value: any, ...)` → argument-dependent

The first argument that is not blank; blank only if every argument is.

The arguments are read left to right and **evaluation stops at the first non-blank one** — what stands to its right is never evaluated. That is a guarantee, not an optimisation: it is what makes `coalesce(x, 1/0)` safe.

Operands: any type.

Sample query:

```kql
// coalesce: region, falling back to country.
FIND customers c
FETCH coalesce(c.region, c.country) region_or_country
```

### Generated SQL

**all dialects**

```sql
-- coalesce: region, falling back to country.
SELECT
  coalesce(c.region, c.country) AS region_or_country
FROM
 customers c
```


## nullif

`nullif(value: any, when: any)` → argument-dependent

*value*, or blank when it equals *when* — the inverse of `coalesce`, and the way to turn a placeholder such as `0` or an empty string into a real blank.

The two are **compared**, so they must be able to meet in one type, and the result takes that common type rather than *value*'s own. `nullif(quantity, 0.5)` therefore comes out decimal, not integer — the comparison promotes *value* before it is returned. Comparing across type groups, say text with a number, is an error.

Operands: any type.

Sample query:

```kql
// nullif: treat a reorder level of 0 as "not set" rather than as a real threshold.
FIND products p
FETCH p.product_id ASC, nullif(p.reorder_level, 0) reorder_level_or_blank
```

### Generated SQL

**all dialects**

```sql
-- nullif: treat a reorder level of 0 as "not set" rather than as a real threshold.
SELECT
  p.product_id
, nullif(p.reorder_level, 0) AS reorder_level_or_blank
FROM
 products p
ORDER BY
  p.product_id ASC
```


## case

`case(condition: BOOLEAN, result: any, ...)` → argument-dependent

Tests each condition in order and returns the result paired with the first one that holds. A trailing argument without a condition is the fallback; without it, a row that matches nothing comes out blank.

All the results must be able to meet in **one type** — that is what the column will hold, and it is decided once for the whole expression, not per row. Mixing text and numbers across the branches is therefore an error, not a per-row surprise.

Like `coalesce`, only the branches that are needed get evaluated: testing stops at the first condition that holds.

| Argument | Type | Description |
|---|---|---|
| condition | BOOLEAN | a WHEN test evaluated in order until one is true |
| result | any | the value returned when its paired condition is true |

Sample query:

```kql
// case: classify orders by freight.
FIND orders o
FETCH case(o.freight > 100, 'high', 'low') freight_tier
```

### Generated SQL

**all dialects**

```sql
-- case: classify orders by freight.
SELECT
  CASE WHEN o.freight > 100 THEN 'high' ELSE 'low' END AS freight_tier
FROM
 orders o
```

