# Package `ai.koryki.scaffold`

`ai.koryki.scaffold` provides the two-layer structural and semantic description of a
relational database used by the **koryki** platform.

- The **schema layer** (`schema/`) models the raw database structure: tables, columns,
  types, primary keys, and foreign-key relationships, exactly as they exist in the database.
- The **domain layer** (`domain/`) models the same database at a semantic level: named
  entities, typed attributes. Named links between entities group foreign key relationships with same 
  meaning under a single name.


Higher-level components (the IQL query engine, the SQL renderer, the validator) work
primarily with the domain layer, but resolve physical join columns and table names by
falling back to the schema layer.

## Sub-packages

| Sub-package | Responsibility |
|---|---|
| `schema/` | Raw structural database model — tables, columns, types, PKs, FK relations |
| `domain/` | Semantic domain model — entities, attributes, links and their business metadata |



## Package `ai.koryki.scaffold.schema`

Provides a plain Java object graph that represents the physical structure of a relational
database schema. It is the lowest-level representation of "what the database looks like":
table names, column names, data types, nullability, primary-key positions, and foreign-key
relationships between tables. As table and column names might not fit to **IQL** or **KQL** identifiers
we need schema as abstraction layer.



The graph is rooted at `Schema`, which acts as the aggregate root. All access to tables
and relations is mediated through `Schema` instances. The graph is intentionally data-only
(no business logic); validation, join resolution, and query compilation are performed by
consumers in `ai.koryki.iql`.

### Object graph

```
Schema
 ├── List<Table>
 │    └── List<Column>
 └── List<Relation>
      ├── startTable  : String   (table name reference)
      ├── endTable    : String   (table name reference)
      ├── startColumns: List<String>  (column name references)
      └── endColumns  : List<String>  (column name references)
```

> `Relation` references tables and columns by **name string**, not by object reference.
> Resolution back to `Table` / `Column` objects happens at runtime via
> `Schema.linkRelations(...)`.



### Class overview

| Class | Role |
|---|---|
| [`Schema`](#schema) | Aggregate root. Owns all `Table` and `Relation` objects for one database schema. |
| [`Table`](#table) | A single database table with its list of columns. |
| [`Column`](#column) | A single column: name, type information, nullability, and PK position. |
| [`Relation`](#relation) | A directional foreign-key relationship between two tables (referenced by name). |



### `Schema`

Aggregate root representing an entire database schema. Provides named lookups for tables
and relations and hosts the static `deepCopy` family of methods.

#### Fields

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Technical identifier of the schema (e.g. the database or catalogue name). |
| `label` | `String` | Human-readable display name; may differ from `name` for UI contexts. |
| `comment` | `String` | Short inline comment, typically sourced from `COMMENT ON SCHEMA` DDL metadata. |
| `description` | `String` | Longer free-text description of the schema's purpose; used for documentation and AI-assisted query generation. |
| `tables` | `List<Table>` | Ordered list of tables belonging to this schema. Never `null`. |
| `relations` | `List<Relation>` | Ordered list of foreign-key relations defined across the schema's tables. Never `null`. |



### `Table`

Represents a single database table within a schema. Owns an ordered list of `Column`
objects and provides name-based column lookup.

#### Fields

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Technical table name as it appears in the database. |
| `label` | `String` | Human-readable display name for the table. |
| `comment` | `String` | Short inline comment, typically sourced from `COMMENT ON TABLE` DDL metadata. |
| `description` | `String` | Longer free-text description of the table's purpose. |
| `columns` | `List<Column>` | Ordered list of columns in this table. Never `null`. |


### `Column`

Represents a single column within a table. Carries the column's type information in two
forms (generic and dialect-specific), its nullability, and its primary-key ordinal
position.

#### Fields

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Technical column name as it appears in the database. |
| `label` | `String` | Human-readable display name for the column. |
| `comment` | `String` | Short inline comment, typically sourced from `COMMENT ON COLUMN` DDL metadata. |
| `description` | `String` | Longer free-text description of the column's meaning. |
| `genericType` | `String` | Database-agnostic type name (e.g. `INTEGER`, `VARCHAR`). Used for portability. |
| `dialectType` | `String` | Raw DDL type from the target database (e.g. `nvarchar(15)`, `timestamp(6)`). |
| `nullable` | `Boolean` | Tri-state nullability flag: `true` = nullable, `false` = NOT NULL, `null` = unknown. |
| `pkPos` | `int` | Primary-key ordinal position. `0` means the column is not part of the primary key; a positive value indicates its 1-based position in a (potentially composite) PK. |


### `Relation`

Represents a directional foreign-key relationship between two tables. Tables and columns
are referenced by **name string**, not by object reference; resolution to live `Table` /
`Column` instances is performed at runtime by `Schema.linkRelations(...)`.

Composite foreign keys (where multiple columns participate) are supported via the parallel
`startColumns` / `endColumns` lists.

#### Fields

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Technical name of the FK constraint or relation (e.g. the database constraint name). |
| `comment` | `String` | Short inline comment for the relation. |
| `description` | `String` | Longer free-text description of the relationship's business meaning. |
| `startTable` | `String` | Name of the FK source table (the table that holds the foreign key columns). |
| `endTable` | `String` | Name of the FK target table (the table being referenced). |
| `startColumns` | `List<String>` | Ordered list of FK column names on the source (`startTable`) side. Parallel to `endColumns`. |
| `endColumns` | `List<String>` | Ordered list of referenced column names on the target (`endTable`) side. Parallel to `startColumns`. |
| `symmetric` | `boolean` | When `true`, the relationship is considered bidirectional; the start/end distinction is not significant for join resolution. |


# Package `ai.koryki.scaffold.domain`

`ai.koryki.scaffold.domain` is the **semantic layer** of the scaffold. It mirrors the
physical structure of `ai.koryki.scaffold.schema` but replaces database-centric concepts
with business-oriented domain concepts and may be restricted to a subset:

| Schema layer | Domain layer |
|---|---|
| `Schema` | `Model` |
| `Table` | `Entity` |
| `Column` | `Attribute` |
| `Relation` | `Link` |

The key addition over the schema layer is a **name-override** mechanism: each domain object
optionally carries a physical name (`Entity.table`, `Attribute.column`) that decouples the
business-facing name used in queries from the actual database identifier. `Model.getTable()`
and `Model.getColumn()` use these overrides to translate domain names back to physical names
for SQL rendering.


`Model` is also the bridge between the two layers: `Model.deepCopy(Schema)` converts an
entire `Schema` object graph into a `Model` object graph, mapping `Table → Entity`,
`Column → Attribute`, and `Relation → Link`.



## Object graph

```
Model
 ├── List<Entity>
 │    ├── table      : String          (physical table name override)
 │    └── List<Attribute>
 │         └── column : String         (physical column name override)
 └── List<Link>
      ├── relation   : String          (single schema Relation name reference)
      ├── relations  : List<String>    (multiple schema Relation name references)
      ├── nature     : String          ("inverse" = swap start/end during resolution)
      └── base       : String
```

> `Link` references schema relations by **name string**, not by object reference.
> Resolution to physical join columns happens at runtime via `Schema.linkRelations(...)`
> in combination with `Model.getTable()`.



## Class overview

| Class | Role |
|---|---|
| [`Model`](#model) | Aggregate root. Owns all `Entity` and `Link` objects for one domain model. |
| [`Entity`](#entity) | A single domain entity, optionally mapped to a differently-named physical table. |
| [`Attribute`](#attribute) | A single domain attribute, optionally mapped to a differently-named physical column. |
| [`Link`](#link) | A named domain relationship between entities, resolved to schema FK relations at runtime. |



### `Model`

Aggregate root representing the semantic domain model. Mirrors `Schema` from the schema
layer. Provides name-based lookups for entities and links, and two name-resolution helpers
used by the SQL renderer:

- `getTable(String entity)` — resolves a domain entity name to its physical table name,
  using `Entity.table` if set, otherwise falling back to the entity name itself.
- `getColumn(String entity, String attribute)` — resolves a domain attribute name to its
  physical column name, using `Attribute.column` if set, otherwise falling back to the
  attribute name itself.

Also hosts the static `deepCopy` family of methods for both model-to-model cloning and
schema-to-model conversion.

#### Fields

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Name of the domain model. |
| `label` | `String` | Human-readable display name; may differ from `name` for UI contexts. |
| `comment` | `String` | Short inline comment for the model. |
| `description` | `String` | Longer free-text description of the model's purpose. |
| `table` | `String` | Declared field; not exposed via getter/setter — effectively unused at this time. |
| `entities` | `List<Entity>` | Ordered list of domain entities belonging to this model. Never `null`. |
| `links` | `List<Link>` | Ordered list of domain links defined across the model's entities. Never `null`. |



### `Entity`

Represents a single domain entity within a model. Mirrors `Table` from the schema layer.
Owns an ordered list of `Attribute` objects and provides name-based attribute lookup.

The optional `table` field decouples the domain entity name (used in IQL queries) from the
physical database table name. When `table` is set, `Model.getTable()` returns `table`
instead of `name`.

#### Fields

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Domain name of the entity, as used in IQL queries. |
| `label` | `String` | Human-readable display name for the entity. |
| `comment` | `String` | Short inline comment for the entity. |
| `description` | `String` | Longer free-text description of the entity's business meaning. |
| `table` | `String` | Physical table name override. When set, used by `Model.getTable()` instead of `name` to resolve the database table. |
| `attributes` | `List<Attribute>` | Ordered list of attributes belonging to this entity. Never `null`. |



### `Attribute`

Represents a single domain attribute within an entity. Mirrors `Column` from the schema
layer, but carries only business metadata — no type information, nullability, or primary-key
position.

The optional `column` field decouples the domain attribute name (used in IQL queries) from
the physical database column name. When `column` is set, `Model.getColumn()` returns
`column` instead of `name`.

#### Fields

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Domain name of the attribute, as used in IQL queries. |
| `label` | `String` | Human-readable display name for the attribute. |
| `comment` | `String` | Short inline comment for the attribute. |
| `description` | `String` | Longer free-text description of the attribute's business meaning. |
| `column` | `String` | Physical column name override. When set, used by `Model.getColumn()` instead of `name` to resolve the database column. |



### `Link`

Represents a named domain relationship between entities. Mirrors `Relation` from the schema
layer, but operates at the semantic level: it carries a domain-facing name used in IQL
`JOIN` clauses and references one or more schema `Relation` objects by name for physical
join column resolution.

Unlike `Relation`, `Link` does not carry `startTable`, `endTable`, `startColumns`, or
`endColumns` directly. These are resolved at runtime by looking up the referenced schema
`Relation` objects via `Schema.linkRelations(...)`.

#### Fields

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Domain name of the link, as used in IQL `JOIN` clauses. |
| `label` | `String` | Human-readable display name for the link. |
| `comment` | `String` | Short inline comment for the link. |
| `description` | `String` | Longer free-text description of the relationship's business meaning. |
| `nature` | `String` | Directionality hint. `"inverse"` causes the resolver to swap the start/end table roles when matching against the referenced schema `Relation`. |
| `relation` | `String` | Name of a single schema `Relation` this link maps to. Used when the link resolves to exactly one FK constraint. Mutually exclusive with `relations`. |
| `relations` | `List<String>` | Names of multiple schema `Relation` objects this link may resolve to. Used when one domain link spans or may match several FK constraints. Mutually exclusive with `relation`. |
| `base` | `String` | Base entity name hint used during link resolution to identify the owning side of the relationship. |


### Coupling `Model` to `Schema`


#### `Model`

Entity names an attribute names must fit **IQL** and **KQL** identifier rules and also support internationalization:


    {
      "name" : "vdsession",
      "label" : "Sessionsicht",
      "table" : "v$session",
      "attributes" : [ {
        "name" : "benutzer",     ←     internationalization !
        "label" : "Benutzer",
        "column" : "username"
      }, {
        "name" : "serial",
        "label" : "Serial",
        "column" : "serial#"
      }, {
        "name" : "sid",
        "label" : "SID",
        "column" : "SID"
      } ]
    }

#### `Schema`

Inside schema definition we will write:

    {
      "name" : "v$session",
      "label" : "Session-view",
      "columns" : [ {
        "name" : "username",
        "label" : "Username",
        "genericType" : "varchar",
        "dialectType" : "character varying",
        "nullable" : false,
        "pkPos" : 0
      }, {
        "name" : "serial#",
        "label" : "Serial",
        "genericType" : "varchar",
        "dialectType" : "character varying",
        "nullable" : false,
        "pkPos" : 0
      }, {
        "name" : "SID",
        "label" : "SID",
        "genericType" : "varchar",
        "dialectType" : "character varying",
        "nullable" : false,
        "pkPos" : 1
      }

Depending on database dialect we also need to care about upper/lower case or quoting.
