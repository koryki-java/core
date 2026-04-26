# KQL Grammar

## Query


![query](kql/query.png )

Query is the root rule of KQL-Language
- at least a query has one set
- optionally a query has one or more blocks

## Block

![block](kql/block.png)

Block is an id followed by set or id followed by placeholder.

## Set

![set](kql/set.png)

Set is an operation on sets or a select.

## Select

![select](kql/select.png)

Select has four clauses:
- FIND with one source optionally followed by a list of links
- optionally filter_ clause
- optionally fetch_clause
- optionally limit_clause


## Link

![link](kql/link.png)

Link has three alternatives:
- optionally from=ID of linked source, followed by source.
  If from=ID is missing, source is implicitly linked to the
  previous source in linklist.
- optionally ID of linked source, criteria-ID and source.
- optionally ID of linked source, source and criteria-ID.

Alternatives two and three are semantically identically.
Its for input-convenience as we want the user to choose order of
source and criteria.
Position of "-" indicates that the next ID is the criteria.
This differentiates alternative two from three.

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

## Source

![source](kql/source.png)

Source has two IDs:
- frist name
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
- frist alias
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
