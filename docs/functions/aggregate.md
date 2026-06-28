---
menuTitle: "Aggregate Functions"
parent: "FUNCTIONS"
order: 7
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Aggregate Functions

## count

`count([, value: any])` → BIGINT *(aggregate)*

Number of input rows or non-null values.

Standard SQL: `count(value)`

Example: `count(o.order_id)`


## avg

`avg(value: numeric | duration)` → FLOAT *(aggregate)*

Average of the input values.

Standard SQL: `avg(value)`

Example: `avg(od.unit_price)`


## sum

`sum(value: numeric | duration)` → argument-dependent *(aggregate)*

Sum of the input values.

Standard SQL: `sum(value)`

Example: `sum(od.quantity)`


## min

`min(value: any)` → argument-dependent *(aggregate)*

Minimum input value.

Standard SQL: `min(value)`

Example: `min(o.order_date)`


## max

`max(value: any)` → argument-dependent *(aggregate)*

Maximum input value.

Standard SQL: `max(value)`

Example: `max(o.order_date)`


## string_agg

`string_agg(value: any, separator: TEXT)` → TEXT *(aggregate)*

Concatenates non-null input values into a string, separated by *separator*.

Standard SQL: `string_agg(value, separator)`

Example: `string_agg(p.product_name, ', ')`

