# Package `ai.koryki.iql`

`ai.koryki.iql` is a compiler/transpiler for the **Intermediate Query Language (IQL)**, an SQL-like domain-specific language used intermediately by the 
**koryki** platform. It is responsible for:

- **Parsing** IQL text into a structured query object model (AST). See package **ai.koryki.iql.query**
- **Rewriting** queries that prepare the bean model for correct SQL generation.
- **Validating** queries semantically against a domain model, database schema and common query pattern.
- **Rendering** validated queries to required SQL dialect


## Package Structure


| Subpackage  | Responsibility                                                                                                                                                             |
|---|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `query/`    | Plain Java bean classes forming the complete **IQL** AST. Every parsed query is represented as a tree of these objects.                                                    |
| `logic/`    | Normalization of boolean (`LogicalExpression`) trees: flattening, double-negation elimination, De Morgan's law.                                                            |
| `rules/`    | Query rewriting rules that prepare the bean model for correct SQL generation (GROUP BY inference, outer join safety, predicate redistribution, etc.).                      |
| `validate/` | Semantic checks that verify entity names, attribute names, alias references, and aggregate/scalar mixing are all valid relative to the configured domain model and schema. |

---

## Processing Pipeline

```
IQL text
   │
   ▼
IQLReader                 — ANTLR-based lexer + parser (IQL.g4)
   │                        IQLParser.QueryContext (parse tree)
   ▼
IQLQueryMapper            — Maps parse tree nodes to iql.query.* bean objects;
   │                        maintains a parse-node → bean mapping for error reporting
   │                        Query bean model
   ▼
Validator                 — Optional semantic validation, throws ValidatException
   │                        
   │
   ▼
IQLVisibilityContext        — Builds scoped alias→Source lookup tables
   │
   ▼
SqlRenderer               — Renders the bean model to a SQL string
   │                        (SqlQueryRenderer delegates per-SELECT work to SqlSelectRenderer)
   ▼
SQL string
```

The `IQLTranspiler` class is the high-level façade that drives this entire pipeline lazily (each stage is only triggered on demand).

---


## The Query Bean Model (`query/`)

The `query/` subpackage defines the IQL AST as plain Java objects. The tree is rooted at `Query` and mirrors the IQL grammar structure:

```
Query
 ├── List<Block>          (WITH clauses)
 │    └── Set
 └── Set                  (main body)
      ├── Select           (single SELECT)
      │    ├── Source      (FROM / leading table)
      │    ├── List<Join>  (JOINs, each with a nested Source and further Joins)
      │    ├── LogicalExpression   (FILTER / WHERE)
      │    ├── LogicalExpression   (HAVING)
      │    ├── List<Out>   (SELECT columns)
      │    ├── List<Group> (GROUP BY)
      │    └── List<Order> (ORDER BY)
      └── Set (left) + operator + Set (right)   (set operations)
```

**Key node types:**

| Class | Represents |
|---|---|
| `Query` | Root; holds CTE blocks and the main `Set` |
| `Block` | A named CTE (`WITH id AS (...)`) |
| `Set` | A set operation (UNION/INTERSECT/MINUS) or a single `Select` |
| `Select` | A full SELECT statement with all its clauses |
| `Source` | A table/entity reference (name, alias, scoped filters/groups/orders/outs), or a list made into rows: no name, but the `call` `unnest(t.items)` |
| `Join` | A link-driven join between two `Source`s |
| `Exists` | A correlated EXISTS subquery |
| `Expression` | A tagged union for any scalar value (field, literal, function, subquery) |
| `LogicalExpression` | A boolean expression tree (AND/OR/NOT/predicate) |
| `UnaryLogicalExpression` | A leaf predicate (`left op right`, `EXISTS`, or parenthesised group) |
| `Function` | A function call with arguments and an optional `Window` definition |
| `Selection` | A postfix selector chain on an expression: a `base` and its `Selector`s (`t.items@1@sku`) |
| `Selector` | One step of a selection: a position (`index`), a range (`from`/`to`), or a name (`id`, `name`) |
| `Lambda` | The parameters and the body of a lambda argument; only ever an argument of a `Function` |
| `Out` / `Group` / `Order` | SELECT list item / GROUP BY item / ORDER BY item |

Every bean created by `IQLQueryMapper` is registered in a `Map<Object, RuleContext>` that maps each bean back to its source parse-tree node. This allows all downstream stages (validation, SQL rendering) to produce error messages with precise source-text locations.

---

### The Visitor / Walker Pattern

Tree traversal over the bean model is driven by two cooperating abstractions:

#### Visitor

**`Visitor`** — an interface with a `visit(Deque<Object>, NodeType)` (pre-order, returns `boolean` to control descent) and a `leave(NodeType)` (post-order) method for every AST node type. All methods have default no-op implementations, so implementors override only what they need.

The `Deque<Object>` argument passed to every `visit()` call is the **ancestor stack** at the time of the call — the current node's parent is at the top, the root is at the bottom. This enables context-sensitive logic without field state (e.g., "find the enclosing `Select`", "find a `Source` by alias in the parent scope"). 

`Visitor` provides static utility methods for common deque queries:

- `parentSelect(deque)` — walks up the stack to the nearest enclosing `Select`
- `findTableInSelect(select, alias)` — finds a `Source` by alias within a specific `Select`
- `getNthElement(deque, n)` — retrieves the n-th ancestor

**`Collector<V>`** — extends `Visitor` and adds a single `collect()` method. Implementations accumulate a result of type `V` during the walk (e.g., a `Map`, a `Boolean`, a `List<Violation>`).

#### Walker

**`Walker`** — drives the traversal. Its `walk(node, visitor)` methods push/pop the current node onto the ancestor stack, call `visitor.visit()`, recurse into children if `visit()` returned `true`, then call `visitor.leave()`. The static `apply()` factory methods combine walking with collecting:

```java
// Walk a query and collect a Map<String, Block> of all block IDs
Map<String, Block> blocks = Walker.apply(query, new BlockRegistryCollector());
```

All internal analysis passes — alias collection, recursion detection, validation, rules — are implemented as `Visitor` or `Collector` subclasses driven by `Walker`.



## SQL Generation

SQL generation is split across two levels, matching the two levels of the IQL grammar:

**`SqlRenderer`** (interface) — the top-level contract. Accepts the full `Query`, a `LinkResolver`, an `IQLVisibilityContext`, and the parse-node mapping. Implemented by `SqlQueryRenderer`.

**`SqlQueryRenderer`** — handles the outer structure

**`SqlSelectRenderer`** — handles the full SELECT body

### A renderer is for one caller at a time

`SqlQueryRenderer` keeps the query it is rendering — the query, its visibility scope and the map
from IQL nodes to their source — in fields, from the start of `toSql` to its end. Two threads
rendering on one instance write into each other's state, and what comes out is another query's SQL,
or an exception from a scope that belongs to a different statement. Measured: five queries of
different shapes from eight threads on one instance, and about a quarter of the results were wrong.

The way to hold a renderer is therefore to make one for each use — `new SqlQueryRenderer(dialect,
zone)` is a few assignments — and `Generator`, `Engine` and `EngineBuilder` do it for you when they
are given the means to make one instead of a renderer:

```java
Engine<HeaderInfo, ListResult<HeaderInfo>> engine =
        EngineBuilder.headers(database, resolver, () -> new SqlQueryRenderer(dialect, zone)).build();
```

The engine asks that supplier for a renderer for every operation and drops it afterwards, so it
holds no renderer and can be built once and shared. `toSql`, `executeKQL`, `validateKQL`,
`warningsKQL` and `analyze` each use one renderer, for the function catalog and the dialect as well
as for rendering. The constructors that take a renderer instance are deprecated: that one instance
then renders every query, and the engine is for one caller at a time again. What may be shared
either way is the dialect, the `LinkResolver`, and a database as its `allowsConcurrentExecution()`
says (see [JDBC.md](JDBC.md)).

## Link Resolution (`LinkResolver`)

IQL queries reference joins by **link names** 
(domain-level relationship names, e.g., `orders`, `customer`) rather than 
physical FK column names. The `LinkResolver` bridges the gap.

`LinkResolver` is also responsible for mapping **IQL** identifiers (lowercase letters and digits) to the database 
identifiers, which are less restrictive (`getDialectTable(source)`, `getDialectColumn(source, field)`).

### The Rules Pipeline (`rules/`)

The rules pipeline is applied **only on the KQL path** (via `KQLTranspiler`) before SQL generation. It transforms the raw bean model output of `KQLQueryMapper` into a form that maps correctly and safely to SQL. Rules are applied in a fixed order by `Rules.apply()`:

| Order | Rule | Effect |
|---|---|---|
| 1 | `PushOutRule` | Moves `select.out` items into the owning `source.out` based on the referenced alias |
| 2 | `InferJoinColumnsToBlockRule` | Ensures FK columns needed for a join condition are present in the CTE's `out` list |
| 3 | `HavingRule` | Moves predicates containing aggregate functions from `select.filter` to `select.having` |
| 4 | `GroupRule` | Auto-generates `GROUP BY` items from non-aggregate `OUT` expressions when aggregates are present |
| 5 | `IdentityRule` | Replaces the `ID` identity placeholder with the actual primary-key column, resolved from the schema |
| 6 | `SelectorNamesRule` | Translates the names of struct fields in selectors from the words of the model to the keys of the column's concept (`t.address@breitengrad` to `t.address@latitude`), and the fields of the alias of an `unnest` (`i.artikel` to `i.sku`) |
| 7 | `PushLogicalExpressionRule` | Moves filter/having predicates that reference only one source into that source's own `filter`/`having` (critical for correctness of LEFT OUTER JOINs) |
| 8 | `PushGroupRule` | Moves `select.group` items into the corresponding `source.group` |
| 9 | `PushOrderRule` | Moves `select.order` items into the corresponding `source.order` |
| 10 | `CheckOuterJoinFilterRule` | Validates that no predicate on an outer-joined (optional) source remains in the top-level `select.filter`, which would silently convert it to an inner join |



### Validation (`validate/`)

Semantic validation is run after bean construction and before SQL generation. `Validator` runs its checks one after the other, each as a `Walker` pass:

**`FunctionValidator`** — detects structurally invalid expressions: an expression that simultaneously contains an aggregate function (e.g., `SUM`, `COUNT`) and a raw column reference is flagged, as this cannot be correctly classified as either a SELECT item or a HAVING predicate.

**`SelectorValidator`** and **`LambdaValidator`** — check selector chains against the type of the value they select from, and lambda parameters, arity and body against the function that takes them (see [Nested values](#nested-values-selections-and-lambdas)).

**`SourceCallValidator`** — a source that is a call may only be `unnest`, with one list argument (violations of category `source`); the rows of it are typed on the fly by `UnnestElements` from the type of the list, and kept in the visibility context, not in the beans (see [Nested values](NESTED.md#unnest-one-row-per-element)).

**`DurationValidator`** and **`DurationValueValidator`** — check interval and duration arithmetic (the second once a dialect is known), and **`NestedSupportValidator`** refuses a use of nested values that the dialect declares it cannot express (see [NESTED.md](NESTED.md)).

**`SchemaValidator`** — verifies all names referenced in the query exist in the configured model/schema:
- Each `Source` name must correspond to a known entity or a declared CTE block ID
- Each `Field` alias must refer to a `Source` that is visible in the current scope
- Each `Field` attribute name must match either a known model attribute or an `OUT` header declared in the referenced entity/CTE

Both validators produce `Violation` objects that carry the offending bean, its source-text `Range`, a human-readable message, and optionally a second `Range` pointing to a related definition. Any violations cause `ValidateException` to be thrown.


### Nested values: selections and lambdas

A selector chain is an `Expression` with a `Selection`; a lambda is an `Expression` with a `Lambda`; a
lambda parameter in a body is an `Expression` with a `parameter`. `KQLQueryMapper` decides by the
lambda scopes around a bare name whether it is a parameter or an alias (`count(o)`), and refuses a
parameter named like an alias in sight.

What a step means depends on the type it meets, which the grammar cannot know. `typing/Selectors`
follows a chain through a `TypeDescriptor` (ARRAY, STRUCT, MAP, JSON) and is used by three consumers:
`SelectorValidator` reports a step that does not fit, `ExpressionTypeResolver` types the result, and
the renderer hands the chain, with the type of its base, to the dialect (`SqlDialect.selection`). A
lambda is typed from its function: `FunctionDefinition` declares the parameter types from the types of
the other arguments, `LambdaValidator` checks the body, and `SqlSelectRenderer.lambda` renders it with
the parameters bound to what the dialect makes of them.

A JSON column whose members the catalog declares carries a `shape` on its `TypeDescriptor`; a selection
that reaches a declared scalar is rendered as the scalar (`jsonScalar` plus a cast), and one that
reaches a declared list of scalars as a list (`jsonList`) where the dialect has list functions.

The user-facing description is [NESTED.md](NESTED.md); `INTERMEDIATE_MODEL.plantuml` shows the beans.


### Identifier Handling (`Identifier`)

The `Identifier` enum centralises all SQL identifier formatting. It defines five modes:

| Mode | Behaviour |
|---|---|
| `neutral` | Uppercase unquoted identifiers; preserves already-quoted identifiers as-is. Used for canonical storage. |
| `normal` | Uppercase; used for comparison/normalisation. |
| `lowercase` | Lowercase; used for SQL output (PostgreSQL convention). |
| `quoted` | Wraps in double-quotes; forces case-preservation. |
| `lowercaseQuoted` | Lowercase + quoted. |

`Identifier.normal(Identifier, String)` is the central normalization method. It handles stripping existing quotes, re-quoting when necessary (e.g., purely numeric identifiers), and applying case transformations. `Identifier.indent(int)` produces indentation strings for formatted SQL output.

