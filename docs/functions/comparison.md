---
menuTitle: "Comparison Operators"
parent: "FUNCTIONS"
order: 11
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Comparison Operators

## =

`left = right` → BOOLEAN

True if *left* equals *right*.

Standard SQL: `left = right`

Example: `c.country = 'Germany'`


## <

`left < right` → BOOLEAN

True if *left* is less than *right*.

Standard SQL: `left < right`

Example: `o.freight < 50`


## <=

`left <= right` → BOOLEAN

True if *left* is less than or equal to *right*.

Standard SQL: `left <= right`

Example: `od.discount <= 0.1`


## >

`left > right` → BOOLEAN

True if *left* is greater than *right*.

Standard SQL: `left > right`

Example: `o.freight > 100`


## >=

`left >= right` → BOOLEAN

True if *left* is greater than or equal to *right*.

Standard SQL: `left >= right`

Example: `od.quantity >= 10`


## LIKE

`string LIKE pattern` → BOOLEAN

True if *string* matches the SQL LIKE *pattern* (`%` and `_` wildcards). Both operands must be TEXT.

Standard SQL: `string LIKE pattern`

Example: `c.company_name LIKE 'A%'`


## BETWEEN

`value BETWEEN low AND high` → BOOLEAN

True if *value* lies within the inclusive range [*low*, *high*].

Standard SQL: `value BETWEEN low AND high`

Example: `o.order_date BETWEEN '1997-01-01' AND '1997-12-31'`


## IN

`value IN (items, …)` → BOOLEAN

True if *value* equals any of the listed *items*.

Standard SQL: `value IN (items)`

Example: `c.country IN ('USA', 'Canada')`


## ISNULL

`value ISNULL` → BOOLEAN

True if *value* is NULL.

Standard SQL: `value IS NULL`

Example: `o.shipped_date ISNULL`

