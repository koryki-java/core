# Plan: turning a list into rows (UNNEST) without a grammar change

Status: planned for the next release. Nothing of this is built. Written after `0.3.0` was prepared;
the measurements are from 2026-10-15.

## The problem

A list cannot be grouped or joined by what is inside it. "Revenue per sku" over `orders.items`, a list
of structs, needs one row for each element. Today that takes a *flatten view* that the generator in
`tools` writes (`FlattenViews`), that a person creates in the database and that is added to the
catalog by hand ([`NESTED.md`](../NESTED.md#flatten-views-one-row-per-element)). For an analyst it is the
most wanted operation, and it is the one that needs a developer.

Per-row questions (an order total from its lines) are already one lambda, `list_sum(list_transform(...))`,
and stay that way. This plan is for grouping and joining.

## Decision

**Virtual tables, made in memory when the catalog is loaded; no grammar change.**

The analyst writes what is written today for a flatten view, with the name of the list column in place
of the view:

```
FIND orders o, o items i
FETCH i.sku, sum(i.qty * i.price) revenue
```

`link : from=ID? PLUS? source` and `source : name=ID alias=ID` already parse `o items i`. Only the
meaning of `items` is new: a list column of `o`'s entity, as a table of its elements. The outer case
exists already, since `+` in a link is a left join: `o + items i` keeps the orders with an empty list.

The model's own names are used: under a German model `b positionen p`, `p.artikel`, with the field
names and labels that the members and notions already say. Nothing is translated twice.

## How it works

1. **At load**, after the schema and the model are read and checked, each list column becomes a
   *derived* table and entity: `Table` gets a definition (`unnest: {table, column}`), the entity and
   the link to its parent are generated, and the relation is the key of the parent. The objects are
   marked derived: never written back, not saved by an editor, not shown in lists by default.
2. **The structure is made once for a schema**, since it depends only on the schema; the names and
   the wording of a model are put on top for each language. The objects of a derived table are built
   on first use.
3. **Resolution.** `o items i` is resolved relative to `o`'s entity. A real entity or link with the
   name wins; otherwise a list attribute of that name; otherwise the error it is today. An entry in the
   catalog under the same name overrides the generated one (to relabel it, hide it, or point it at
   a materialized view, below).
4. **Rendering.** Where the renderer turns a source name into a table name (`SqlSelectRenderer`, the
   place that calls `getDialectTable`), a derived table is written as an inline `SELECT` of the unnest of
   its parent, in the SQL of the dialect (the builders of `FlattenViews`, five for lists, three for JSON).
5. **Scope.** A derived table is named by its parent and column (`orders$items`, with `orders$items$opts`
   for a list inside the elements); the user never writes that, and it cannot collide with a real
   entity. From the derived table its own lists are reached the same way: `i opts q`.

### Limits that keep it small
- Only the top-level fields of an element become columns; a struct inside stays one struct-valued
  column, reached with `@`.
- Depth: 2 by default, a setting; deeper lists keep selectors and lambdas.
- Lists of lists and of maps, and maps as rows, are left out of the first version.
- A parent without a key is skipped. `unnest: false` on a table or column switches it off; the
  derived entities are listed by `describeEntity`, the editor and the LLM prompt in one line
  ("list columns: items"), not one entity each.

## Execution time

Measured with the same revenue-per-sku query, best of several runs, on synthetic data of one shape
(5 elements per row, evenly spread values, no skew).

| | DuckDB, 2M × 5 | PostgreSQL 17, 500k × 5 |
|---|---:|---:|
| A virtual table as `FlattenViews` writes it (`ordinal` kept), joined to the parent | 1,442 ms | 538 ms |
| B the same, `ordinal` left out | 73 ms | 536 ms |
| C no join to the parent | 47 ms | 380 ms |
| D folded: `FROM parent, UNNEST(...)` | 47 ms | 362 ms |
| E folded, with `ordinal` | 1,915 ms | 362 ms |
| A, parent filtered to 1% | 1,430 ms | 66 ms |
| B, parent filtered to 1% | 12 ms | 66 ms |
| D, parent filtered to 1% | 78 ms | 33 ms |
| per-row lambda, no unnest | 52 ms | n/a |

What follows, and is part of the plan:

1. **Write only the columns the query uses, and `ordinal` only if it is used.** On DuckDB `WITH
   ORDINALITY` costs about 30 times; the join to the parent costs almost nothing. The generator
   writes `ordinal` always today; a virtual table must not.
2. **Fold the pair into one `FROM parent, UNNEST(...)`** when the query joins a derived table to its
   parent along the generated relation. It is worth little on DuckDB and 1.5 to 2 times on PostgreSQL,
   so it is the second step, after rule 1.
3. **A materialized view stays the answer for heavy use**, and above all for lists in JSON (MariaDB,
   SQL Server, SQLite), which are parsed again by every query; not measured. An entry in the catalog
   that points the derived table at a real view or table is the escape hatch.

Not measured: Oracle, Trino, Snowflake, SQL Server, MariaDB, SQLite, long lists, skewed values, wide
structs. Oracle's `TABLE()` in a derived table is where the VARRAY fault lives
([`NESTED.md`](../NESTED.md#oracle-an-element-of-a-varray)); do not assume it behaves.

## Semantics to state in the docs, not to hide
- An empty or NULL list has no rows; `+` keeps the parent.
- Snowflake `FLATTEN` drops NULL elements; the ordinal of an Oracle nested table follows storage order
  (catalogs should prefer VARRAY).
- `ordinal` counts from 1.

## Steps

1. **Catalog.** `Table.unnest`, the loader check (the parent has the column, a key, a list type), the
   generation of derived tables, entities and links, the rules above for names, precedence, hiding and
   `unnest: false`. Exit: the complex bed and the shared bed list their derived entities; nothing is
   saved by the demo editor.
2. **Renderer, DuckDB and PostgreSQL first.** The derived `SELECT`, used columns only, `ordinal` only if
   used. Exit: `FIND check_complex t, t items i ...` gives the answers of the flatten views of today
   (`flatten_items.kql` and its goldens).
3. **The other dialects**, one at a time against the live bed: Trino, Snowflake, Oracle, then the JSON
   ones. Exit for each: the same answers as DuckDB, with an empty list and a NULL list in the data.
4. **The fold** into `FROM parent, UNNEST(...)`.
5. **Messages and docs**: errors in the model's names, `NESTED.md`, the README, the changelog.

## Tests that belong to it
- **Generated data, all engines, compared with DuckDB.** Seeded random rows: empty, NULL, one, many,
  duplicates, NULL elements, unicode, nested; table shapes that differ (with and without a JSON
  column: the Oracle fault depended on it). This comes first and protects everything below.
- **Timing.** The unnest query at 10k and 1M parents per dialect, with a limit above which the build
  says so.
- The shared bed (`check_shared`) and the complex bed as they are: a list that is empty after one that
  is not, and a NULL list.

## Why not a keyword

`FIND orders o, o UNNEST items i` was considered. It says plainly what happens, needs no rule for a
name that is both an entity and a list column, and gives the folded SQL by construction. Against it: a
grammar change reaches the ANTLR grammar and the larger one in visualize, both mappers, the
serializers and the formatter, the translator, the scope collectors, the rules, the validators, every
dialect's renderer, the reference docs, the LLM prompts and the editor's completion, as one coordinated
release; `unnest` becomes a reserved word; and the alias still needs the wording of the element's
fields, which is the machinery of a virtual table. It does not run faster by itself.

Revisit it as sugar over the same machinery if, in use, the name rule of step 3 of "How it works"
confuses models or people.

## Related, in this order after the above
- `Schema.language`, so that the audit knows when a word that is still the schema's is a gap.
- The names of the model in the messages about function arguments (`PartNames`).
- Alternatives in a refusal ("`list_reduce` is not available on Oracle; `list_sum`, `list_min`,
  `list_max` and `list_agg` are") and a stated portable subset.

## Appendix: the queries that were timed

DuckDB, `orders(nr, region, items list<struct(sku, qty, price)>)`; PostgreSQL, the same with a composite
type. Shapes, with `D` the folded form:

```sql
-- A: as generated today
SELECT i.sku, sum(i.qty * i.price)
FROM orders o
JOIN (SELECT t.nr, u.ordinal AS ord, u.item.sku AS sku, u.item.qty AS qty, u.item.price AS price
      FROM orders t, UNNEST(t.items) WITH ORDINALITY AS u(item, ordinal)) i ON i.nr = o.nr
GROUP BY i.sku;

-- D: folded
SELECT u.item.sku, sum(u.item.qty * u.item.price)
FROM orders o, UNNEST(o.items) AS u(item)
GROUP BY 1;
```

B drops `ordinal`, C drops the join, E keeps it in the folded form, the primed rows add
`WHERE o.region = 'r7'` (1% of the parents).
