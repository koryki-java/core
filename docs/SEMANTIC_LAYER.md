# Semantic Layer — `ai.koryki.catalog`

The semantic layer bridges the physical database schema and the human-readable domain model that KQL queries operate on.
It is built from two complementary descriptions — **schema** and **domain model** — held together by a type system.

---

## Package `ai.koryki.catalog.schema`

Describes the physical structure of the database.

### `Schema`
Top-level container for a database schema.

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Schema identifier |
| `label` | `String` | Human-readable label |
| `comment` | `String` | Short comment |
| `description` | `String` | Long description |
| `schemaPrefix` | `String` | Optional. The database schema the tables live in; the renderer writes it in front of every base table (`sales.customers`), never in front of a block. One name, not a path. Left out of the JSON when unset. |
| `tables` | `List<Table>` | All tables in the schema |
| `relations` | `List<Relation>` | Foreign-key relations between tables |

### `Table`
A single database table.

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Table name |
| `label` | `String` | Human-readable label |
| `comment` | `String` | Short comment |
| `description` | `String` | Long description |
| `columns` | `List<Column>` | Columns of this table |

### `Column`
A single column within a table. What the type of the column is, and how it is worded, is said by
its concept.

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Column name |
| `concept` | `Concept` | The concept of this column alone |
| `conceptRef` | `String` | The name of a concept in `Schema.concepts`, instead of `concept` |
| `nullable` | `Boolean` | Whether the column allows NULL |
| `pkPos` | `int` | Primary key position (0 = not part of PK) |
| `namePos` | `int` | Position in the business name of the row (0 = not part of it) |

### `Concept`
What a column, or a part of a container, is: its wording and its type.

| Field | Type | Description |
|---|---|---|
| `label`, `comment`, `description` | `String` | The wording |
| `typeFamily` | `String` | Logical type family (e.g. `INTEGER`, `DATE`, `STRUCT`) |
| `typeEncoding` | `String` | Storage encoding declaration (e.g. `EPOCH:SECONDS`, `SCALED:2`, `JSON`) |
| `dialectType` | `String` | Raw SQL type as declared in the dialect; for a container, a spelling that is checked against its entries |
| `unit`, `quantity`, `presentation` | `String` | What the values measure and how they read |
| `arraySize` | `Integer` | The fixed size of an ARRAY |
| `entries` | `Map<String, Entry>` | The parts of a container: the fields of a STRUCT, `element` of an ARRAY, `key` and `value` of a MAP |

### `Entry`
One part of a container: the name of a concept of the schema or a type spelling (`"VARCHAR"`), or a
`Concept` of its own that belongs to this entry alone. In `db.json` it is a string or an object.

### `Relation`
A foreign-key relationship between two tables.

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Relation name |
| `comment` | `String` | Short comment |
| `description` | `String` | Long description |
| `startTable` | `String` | Source table |
| `endTable` | `String` | Target table |
| `symmetric` | `boolean` | Whether the relation is navigable in both directions |
| `startColumns` | `List<String>` | Foreign key columns on the source side |
| `endColumns` | `List<String>` | Referenced columns on the target side |

---

## Package `ai.koryki.catalog.domain`

Describes the semantic (business) model on top of the physical schema.
Entities and links are the vocabulary KQL queries are written in.

### `Model`
Top-level container for a domain model.

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Model identifier |
| `label` | `String` | Human-readable label |
| `table` | `String` | Default backing table |
| `comment` | `String` | Short comment |
| `description` | `String` | Long description |
| `entities` | `List<Entity>` | All entities in the model |
| `links` | `List<Link>` | Associations between entities |
| `notions` | `Map<String, Notion>` | What the model says about the concepts the schema has by name, under the name of the concept |

### `Entity`
A named domain concept backed by a physical table.

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Entity name (used in KQL) |
| `label` | `String` | Human-readable label |
| `comment` | `String` | Short comment |
| `description` | `String` | Long description |
| `table` | `String` | Backing physical table |
| `attributes` | `List<Attribute>` | Attributes of this entity |

### `Attribute`
A field of an entity, mapped to a physical column.

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Attribute name (used in KQL) |
| `label` | `String` | Human-readable label |
| `comment` | `String` | Short comment |
| `description` | `String` | Long description |
| `column` | `String` | Physical column this attribute maps to |
| `members` | `Map<String, Member>` | The parts of the nested value of the column in the language of the model, by the physical key of the entry: the fields of a struct a query writes after `@`, and under `element`, `key` and `value` the parts inside a list or a map |

### `Member`
How one part of a nested value is named and described in the language of a model.

| Field | Type | Description |
|---|---|---|
| `name` | `String` | What a query of this language writes for the part (the physical key when absent) |
| `label`, `comment`, `description` | `String` | The wording |
| `members` | `Map<String, Member>` | The parts of this part, when it is a nested value itself |

### `Notion`
What the model says about a concept the schema has by name, so that it is said once for every column that refers to it: `label`, `comment`, `description` and `members`, as a `Member` has them. It is held in `Model.notions` under the name of the concept. A concept of a column's own is not named, and its parts are in `Attribute.members`; what an attribute says wins over the notion of the concept it refers to.

`ai.koryki.catalog.MemberNames` is the one place that reads them: it walks the concept and the names together and says, for a name written in a query, which entry it is, and what the language calls an entry. `LinkResolver` checks them against the schema: a member is an entry of its concept, no two fields have the same name in the language, a notion is the notion of a concept the schema has.

### `Link`
An association between entities, backed by a physical relation.

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Link name |
| `label` | `String` | Human-readable label |
| `comment` | `String` | Short comment |
| `description` | `String` | Long description |
| `nature` | `String` | Cardinality or join nature (e.g. `same_order`) |
| `relation` | `String` | Name of the backing physical `Relation` |
| `base` | `String` | Base entity of this link |
| `relations` | `List<String>` | Additional relation names (for multi-hop links) |

---

## Package `ai.koryki.catalog.schema.types`

Defines the type system used to annotate columns with logical families and storage encodings.

### Interfaces

#### `TypeFamily`
Marker interface for a logical type category.

| Method | Description |
|---|---|
| `name()` | Returns the family name (e.g. `INTEGER`, `DATE`) |
| `accepts(TypeFamily)` | Whether this family accepts a given candidate |

#### `TypeEncoding`
Describes how a value of a given `TypeFamily` is physically stored.

| Method | Description |
|---|---|
| `name()` | Encoding declaration string (e.g. `EPOCH:SECONDS`) |
| `family()` | The `TypeFamily` this encoding belongs to |

---

### Leaf families — `CoreTypeFamily`

Enum implementing `TypeFamily`.

| Constant | Description |
|---|---|
| `INTEGER` | Whole numbers |
| `DECIMAL` | Fixed-point numbers |
| `FLOAT` | Floating-point numbers |
| `BOOLEAN` | True / false |
| `TEXT` | Character strings |
| `DATE` | Calendar date |
| `TIME` | Time of day |
| `TIMESTAMP` | Date and time |
| `INTERVAL` | Duration |
| `BLOB` | Binary |
| `UUID` | Universally unique identifier |
| `JSON` | Structured JSON |

---

### Family groups — `FamilyGroup`

A named umbrella over several `CoreTypeFamily` leaf values.

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Group name |
| `members` | `Set<TypeFamily>` | Leaf families included in this group |

Predefined groups in `Families`:

| Constant | Members |
|---|---|
| `NUMERIC` | `INTEGER`, `DECIMAL`, `FLOAT` |
| `TEMPORAL` | `DATE`, `TIME`, `TIMESTAMP`, `INTERVAL` |
| `ADDITIVE` | `INTEGER`, `DECIMAL`, `FLOAT`, `INTERVAL` |
| `ANY` | All families |

---

### Encodings

Each encoding implements `TypeEncoding` and carries the information needed to read or write a non-native physical representation.

#### `NativeEncoding`
No extra storage information — value is stored in the family's natural SQL type.
Serialized as `"NATIVE"`.

#### `EpochTypeEncoding`
Point in time stored as an integer count since Unix epoch.
Serialized as `"EPOCH:<unit>"` (e.g. `EPOCH:SECONDS`).

| Field | Type |
|---|---|
| `unit` | `ChronoUnit` |

#### `WallClockEncoding`
Date or timestamp stored as a wall-clock value in a named time zone.
Serialized as `"DATE_WALLCLOCK:<zoneId>"` or `"TIMESTAMP_WALLCLOCK:<zoneId>"`.

| Field | Type |
|---|---|
| `family` | `TypeFamily` (`DATE` or `TIMESTAMP`) |
| `zone` | `ZoneId` |

#### `ScaledTypeEncoding`
Exact numeric stored as an integer of minor units (e.g. cents under `SCALED:2`).
Serialized as `"SCALED:<scale>"`.

| Field | Type |
|---|---|
| `scale` | `int` |

#### `IntervalTypeEncoding`
Duration stored as an integer count of a time unit.
Serialized as `"INTERVAL:<unit>"` (e.g. `INTERVAL:MINUTES`).

| Field | Type |
|---|---|
| `unit` | `ChronoUnit` |

#### `IntervalStringEncoding`
Interval stored as a formatted string (`ISO8601` or `CLOCK`).
Serialized as `"INTERVAL_FROM_STRING:<format>"`.

| Field | Type |
|---|---|
| `format` | `Format` (`ISO8601` \| `CLOCK`) |

#### `CoreTypeEncoding`
Enum of built-in non-native encodings for standard cross-dialect patterns.

| Constant | Family | Description |
|---|---|---|
| `TIME_SECONDS_FROM_MIDNIGHT` | `TIME` | Time as seconds since midnight (integer) |
| `TIME_FROM_DATE` | `TIME` | Time extracted from a date column |
| `TIME_FROM_INTEGER` | `TIME` | Time encoded as integer |
| `TIME_FROM_TIMESTAMP` | `TIME` | Time extracted from a timestamp |
| `TIME_FROM_STRING` | `TIME` | Time parsed from a string |
| `DATE_FROM_EPOCH_DAY` | `DATE` | Date as days since Unix epoch |
| `BOOLEAN_FROM_INTEGER` | `BOOLEAN` | Boolean stored as 0/1 integer |
| `BOOLEAN_FROM_TEXT` | `BOOLEAN` | Boolean stored as text |
| `TEXT_FROM_BOOLEAN` | `TEXT` | Text derived from a boolean |
| `UUID_FROM_BINARY` | `UUID` | UUID stored as binary |
| `UUID_FROM_STRING` | `UUID` | UUID stored as string |
| `INTERVAL_FROM_STRING` | `INTERVAL` | Interval from a string |
| `INTERVAL_INTERVAL` | `INTERVAL` | Native SQL INTERVAL type |
| `INTERVAL_YEAR_MONTH` | `INTERVAL` | Year-month interval |
| `INTERVAL_DAY_SECOND` | `INTERVAL` | Day-second interval |
| `CALENDAR_DISTANCE` | `INTERVAL` | Calendar-based distance |
| `INSTANT` | `TIMESTAMP` | UTC instant |
| `JSON` | `JSON` | A container concept (ARRAY, STRUCT, MAP) stored as a JSON value, in whatever type its dialect type names |

---

### `TypeDescriptor`
Fully resolved type of a column or expression, combining family, encoding, and precision.

| Field | Type | Description |
|---|---|---|
| `typeFamily` | `TypeFamily` | Logical type family |
| `typeEncoding` | `TypeEncoding` | Storage encoding |
| `physicalTypeName` | `String` | SQL type name as seen in the dialect |
| `precision` | `int` | Precision (for DECIMAL / VARCHAR) |
| `scale` | `int` | Scale (for DECIMAL) |

Predefined constants cover the most common types: `DATE`, `TIME`, `TIMESTAMP`, `TEXT`, `BOOLEAN`, `SMALLINT`, `INTEGER`, `BIGINT`, `FLOAT`, `DOUBLE`, `DECIMAL`, `INTERVAL`, `NULL`.
