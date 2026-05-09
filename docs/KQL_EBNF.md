# KQL Grammar

## Query


![query](kql/query.png )

Query is the root rule of KQL-Language.

The optional `WITH` clause introduces named sub-queries (blocks) separated by commas.

The mandatory `set` at the end is the main result expression and may be a plain select or a set 
operation via `UNION`, `UNIONALL`, `MINUS`, or `INTERSECT`.


## Block

![block](kql/block.png)

A `block` defines a named sub-query inside a 
`WITH` clause. The first alternative binds an identifier to a set-operation; 
the second binds an identifier to a placeholder, allowing the sub-query to be injected 
externally at runtime.

## Set

![set](kql/set.png)

A `set` is either a plain `select` or a set-operation via 
`INTERSECT`, `UNION`, `UNIONALL`, or `MINUS`. `INTERSECT` binds 
more tightly than the other operators; parentheses can be used to override precedence.

## Select

![select](kql/select.png)

`select` is the core query construct, built from up to four clauses. 
The mandatory `FIND` clause names the primary source and any linked sources to traverse.
It is followed by an optional `FILTER` clause for predicates, an optional `FETCH` clause defining the 
output expressions, and an optional `LIMIT` 
clause capping the number of returned rows.

**KQL** deliberately omits `GROUP BY`, `HAVING`, and `ORDER BY`. 
Grouping is inferred automatically whenever `FETCH` contains an 
aggregate expression. Aggregate predicates in `FILTER` are promoted to 
`HAVING` by the transpiler. Sort order is declared inline on
each `fetch_item` using `ASC` or `DESC`. This keeps queries 
concise and frees the user from SQL's clause-placement rules.

This omission is a key design feature. In **SQL**, misplacing an 
aggregate expression between `WHERE` and `HAVING`, or forgetting a column 
in `GROUP BY`, are frequent error sources — for both human authors 
and AI-generated queries. By deriving these clauses mechanically from 
the structure of `FIND`, `FILTER`, and `FETCH`, **KQL** eliminates 
an entire class of mistakes. A non-SQL-expert can read and verify a 
KQL select top to bottom without knowing SQL's clause-placement rules, 
and an AI model generating KQL 
needs to reason about far fewer structural constraints than it would 
generate equivalent SQL.

## Find

![select_find](kql/select_find.png)

`FIND` introduces the graph of sources the query operates on. 
It requires exactly one primary `source`, optionally extended by a 
comma-separated list of linked sources.

## Filter

![select_filter](kql/select_filter.png)

The `FILTER` clause narrows the result set by applying a logical predicate to 
the source rows matched by `FIND`. It accepts any `logical_expression`, 
including `AND`/`OR`/`NOT` combinations, comparisons, 
and `EXISTS` sub-queries.

## Fetch

![select_fetch](kql/select_fetch.png)

`FETCH` declares what the `query` returns, projecting one or more 
expressions from the matched source rows. The optional `DISTINCT` 
keyword suppresses duplicate rows, and the optional 
`ROLLUP` keyword appends subtotal rows across grouping levels.

## Link

![link](kql/link.png)

A `link` declares an additional source to join to the source graph. 
The optional first identifier specifies which 
already-declared `source` to join from; when absent, the `link` is 
implicitly attached to the preceding `source` in the list.

When two sources share more than one relationship, a criteria identifier 
is mandatory (second or third alternative in diagram). 
Literal `-` prepends the criteria identifier to select the relationship to use.

Alternatives two and three are semantically identically.
Its for input-convenience as we want the user to choose order of
source and criteria.

Literal `+` produces an optional `link` (LEFT OUTER JOIN), preserving rows
even when no matching counterpart is found.

## Logical Expression

![logical_expression](kql/logical_expression.png)

Logical_expression resolves to boolean result.

## Unary Logical Expression

![unary_logical_expression](kql/unary_logical_expression.png)

Unary_logical_expression resolves to boolean result. It has four main alternatives:
- expression and operator optionally followed by an expression, a pair of expression or a set of expressions
- recursive logical_expression in braces
- exists clause
- expression with placeholder, operation is optional, if missing placeholder must set operator too


## Limit Clause

![limit_clause](kql/limit_clause.png)

## Exists

![exists](kql/exists.png)

## Existslink

![existslink](kql/existslink.png)

## Source

![source](kql/source.png)

Source has two IDs:
- first name
- second alias

## Filter Clause

![filter_clause](kql/filter_clause.png)

## Fetch Clause

![fetch_clause](kql/fetch_clause.png)

## Fetch Item

![fetch_item](kql/fetch_item.png)

## Expression

![expression](kql/expression.png)

## Function

![function](kql/function.png)

## Argument

![argument](kql/argument.png)

## Field

![field](kql/field.png)

Source has two IDs:
- first alias
- second name

## Window

![window](kql/window.png)

## Window Order

![order](kql/order.png)

## Frame

![frame](kql/frame.png)

## Window Limit

![window_limit](kql/window_limit.png)

## Date literal

![date_literal](kql/date_literal.png)
