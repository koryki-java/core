
The logical connectives combine **conditions** — a comparison, an `ISNULL` test, an `EXISTS`
subquery, anything that yields yes or no, or a parenthesized group of those:

    condition AND condition → condition
    condition OR condition → condition
    NOT condition → condition

A condition is usually a comparison, but anything that already answers yes or no stands on its own.
A function that asks a yes/no question — `starts_with`, `regexp_like` — is a condition by itself:

    FIND customers c
    FILTER starts_with(c.company_name, 'A')
    FETCH c.customer_id

So is a BOOLEAN column — no `= true` needed:

    FIND products p
    FILTER p.discontinued
    FETCH p.product_id

That holds whether the column is a real boolean or one stored as 0/1 or `'Y'`/`'N'`. The schema
records which, and the generated SQL is written to suit both the storage and the database:
`discontinued` is kept as an integer, so this renders as `p.discontinued <> 0`.

Anything that is *not* a yes/no value must be compared with an operator — `FILTER c.company_name` is
rejected, because a name is not a condition. KQL has no `true`/`false` literal, so a condition is
always a column, a test, or a call, never a written-out boolean.

## Precedence

`NOT` binds tightest, then `AND`, then `OR` — the same order as SQL. So

    FILTER a OR b AND c

means `a OR (b AND c)`, and `NOT a AND b` means `(NOT a) AND b`. Parentheses override
this, and the group you write is carried through to the generated SQL.

The keywords are case-sensitive: `AND`, `OR` and `NOT` must be uppercase. Lowercase
`and` lexes as an identifier, not as an operator.

The operators **AND** and **OR** are commutative: you can swap the left and right
operands without changing the result. The truth tables below rely on that and list each
operand pair once.

## Three-valued logic

SQL uses a three-valued logic system with true, false, and null, which represents
“unknown”. Observe the following truth tables:

| a     | b     | a AND b | a OR b |
|-------|-------|---------|--------|
| TRUE  | TRUE  | TRUE    | TRUE   |
| TRUE  | FALSE | FALSE   | TRUE   |
| TRUE  | NULL  | NULL    | TRUE   |
| FALSE | FALSE | FALSE   | FALSE  |
| FALSE | NULL  | FALSE   | NULL   |
| NULL  | NULL  | NULL    | NULL   |

| a     | NOT a |
|-------|-------|
| TRUE  | FALSE |
| FALSE | TRUE  |
| NULL  | NULL  |

A `FILTER` (and a `HAVING`) keeps a row only when its predicate evaluates to **TRUE**.
False and unknown are both dropped, which is what makes the third value matter in
practice:

- A predicate and its negation do not partition the table. `orders.freight` is nullable,
  so `FILTER o.freight > 100` and `FILTER NOT o.freight > 100` together return fewer rows
  than the unfiltered query — every order with no freight fails both.
- To let unknown through, ask for it: `FILTER NOT o.freight > 100 OR o.freight ISNULL`.

The same rule bites hardest under `NOT` with a subquery. If the subquery of

    FIND customers c
    FILTER NOT c.customer_id IN (
        FIND orders o
        FETCH o.customer_id
    )
    FETCH c.company_name

yields even one NULL, no row qualifies: the membership test is unknown rather than false,
and unknown is dropped. Prefer `NOT EXISTS`, which is immune, or exclude the NULLs in the
subquery.

## Evaluation order

The operands of `AND` and `OR` are **not** evaluated in a fixed order — on any dialect.
The SQL standard does not require left-to-right evaluation, each engine's optimiser
reorders predicates by cost, and KQL itself pushes the conjuncts of a `FILTER` onto
individual sources so that outer joins keep their semantics. Two operands written side by
side may therefore not even end up in the same clause of the generated SQL. The **AND**
sample below shows it: the two comparisons come back swapped.

So never use `AND` or `OR` to guard against an error. In

    FIND order_details od
    FILTER NOT od.quantity = 0 AND od.unit_price / od.quantity > 5

the division is not protected by the left operand and may still be evaluated. Use `case`,
which tests its conditions in order and stops at the first match. Because `case` yields a
value rather than a predicate, the guard moves inside it and the comparison wraps it:

    FIND order_details od
    FILTER case(od.quantity = 0, 0, od.unit_price / od.quantity) > 5
