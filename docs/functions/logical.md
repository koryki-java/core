---
menuTitle: "Logical Operators"
parent: "FUNCTIONS"
order: 12
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Logical Operators

## AND

`left AND right` → BOOLEAN

True if both operands are true.

Standard SQL: `left AND right`

Example: `o.freight > 50 AND c.country = 'USA'`


## OR

`left OR right` → BOOLEAN

True if either operand is true.

Standard SQL: `left OR right`

Example: `c.country = 'USA' OR c.country = 'Mexico'`


## NOT

`NOT operand` → BOOLEAN

Negates the operand.

Standard SQL: `NOT operand`

Example: `NOT o.shipped_date ISNULL`

