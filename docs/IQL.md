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
VisibilityContext          — Builds scoped alias→Source lookup tables
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
| `Source` | A table/entity reference (name, alias, scoped filters/groups/orders/outs) |
| `Join` | A link-driven join between two `Source`s |
| `Exists` | A correlated EXISTS subquery |
| `Expression` | A tagged union for any scalar value (field, literal, function, subquery) |
| `LogicalExpression` | A boolean expression tree (AND/OR/NOT/predicate) |
| `UnaryLogicalExpression` | A leaf predicate (`left op right`, `EXISTS`, or parenthesised group) |
| `Function` | A function call with arguments and an optional `Window` definition |
| `Out` / `Group` / `Order` | SELECT list item / GROUP BY item / ORDER BY item |

Every bean created by `IQL2Bean` is registered in a `Map<Object, RuleContext>` that maps each bean back to its source parse-tree node. This allows all downstream stages (validation, SQL rendering) to produce error messages with precise source-text locations.

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

**`SqlRenderer`** (interface) — the top-level contract. Accepts the full `Query`, a `LinkResolver`, a `VisibilityContext`, and the parse-node mapping. Implemented by `SqlQueryRenderer`.

**`SqlQueryRenderer`** — handles the outer structure

**`SqlSelectRenderer`** — handles the full SELECT body

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
| 6 | `PushLogicalExpressionRule` | Moves filter/having predicates that reference only one source into that source's own `filter`/`having` (critical for correctness of LEFT OUTER JOINs) |
| 7 | `PushGroupRule` | Moves `select.group` items into the corresponding `source.group` |
| 8 | `PushOrderRule` | Moves `select.order` items into the corresponding `source.order` |
| 9 | `CheckOuterJoinFilterRule` | Validates that no predicate on an outer-joined (optional) source remains in the top-level `select.filter`, which would silently convert it to an inner join |



### Validation (`validate/`)

Semantic validation is run after bean construction and before SQL generation. `Validator` orchestrates two passes via `Walker`:

**`FunctionValidator`** — detects structurally invalid expressions: an expression that simultaneously contains an aggregate function (e.g., `SUM`, `COUNT`) and a raw column reference is flagged, as this cannot be correctly classified as either a SELECT item or a HAVING predicate.

**`SchemaValidator`** — verifies all names referenced in the query exist in the configured model/schema:
- Each `Source` name must correspond to a known entity or a declared CTE block ID
- Each `Field` alias must refer to a `Source` that is visible in the current scope
- Each `Field` attribute name must match either a known model attribute or an `OUT` header declared in the referenced entity/CTE

Both validators produce `Violation` objects that carry the offending bean, its source-text `Range`, a human-readable message, and optionally a second `Range` pointing to a related definition. Any violations cause `ValidateException` to be thrown.


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

