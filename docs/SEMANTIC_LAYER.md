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
A single column within a table.

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Column name |
| `label` | `String` | Human-readable label |
| `comment` | `String` | Short comment |
| `description` | `String` | Long description |
| `typeFamily` | `String` | Logical type family (e.g. `INTEGER`, `DATE`) |
| `typeEncoding` | `String` | Storage encoding declaration (e.g. `EPOCH:SECONDS`, `SCALED:2`) |
| `dialectType` | `String` | Raw SQL type as declared in the dialect |
| `nullable` | `Boolean` | Whether the column allows NULL |
| `pkPos` | `int` | Primary key position (0 = not part of PK) |

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
