/*
 * Copyright 2025-2026 Johannes Zemlin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package ai.koryki.tools.flatten;

import ai.koryki.catalog.domain.Attribute;
import ai.koryki.catalog.domain.Entity;
import ai.koryki.catalog.domain.Link;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Relation;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeDescriptorParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * One row per element of a list: a view that flattens a list column of a table, the SQL to create
 * it and the catalog entries that make it an entity like any other.
 *
 * <p>A list cannot be grouped by its elements, joined, or charted: "revenue by sku" asks for one
 * row per element, and what a column holds is one value per row. A view does it once, in the
 * database, in the way each engine does it best ({@code UNNEST}, {@code LATERAL FLATTEN}, {@code
 * TABLE()}), and from then on the elements are rows of an entity with its columns, a relation to
 * the row they come from, and everything the semantic layer gives an entity: labels, units, charts.
 *
 * <p>The view has the key of the table, {@code ordinal} (the position in the list, from 1) and one
 * column for each field of the element, or {@code value} for a list of scalars. Together the key
 * and the ordinal identify a row. The elements of the lists inside the elements stay lists.
 *
 * <p>Known differences: a NULL element of a list of scalars is a row everywhere but on Snowflake,
 * whose FLATTEN leaves it out; and the position of a nested table on Oracle is the order the
 * database happens to return, as it is anywhere on Oracle.
 */
public final class FlattenViews {

    private FlattenViews() {}

    /**
     * The dialects that have lists to flatten, and those that have JSON arrays only (SQLITE,
     * MARIADB, MSSQL): the latter flatten an array a catalog declares inside a JSON column, see
     * {@link #ofJson}.
     */
    public enum Dialect {
        DUCKDB,
        POSTGRESQL,
        TRINO,
        SNOWFLAKE,
        ORACLE,
        SQLITE,
        MARIADB,
        MSSQL
    }

    /**
     * A view and what describes it: its DDL, its table and relation for {@code db.json}, its entity
     * and link for {@code model.json}.
     */
    public record View(
            String name, String sql, Table table, Relation relation, Entity entity, Link link) {}

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    private static final Pattern PLAIN = Pattern.compile("[a-z_][a-z0-9_]*");

    /**
     * The views of the list columns of a table whose elements are structs or scalars.
     *
     * @param columns the columns to flatten, or none for every list column
     */
    public static List<View> of(Schema db, String tableName, Dialect dialect, String... columns) {
        Table table =
                db.getTable(tableName)
                        .orElseThrow(() -> new IllegalArgumentException("no table " + tableName));
        List<Column> keys =
                table.getColumns().stream()
                        .filter(c -> c.getPkPos() > 0)
                        .sorted(Comparator.comparingInt(Column::getPkPos))
                        .toList();
        if (keys.isEmpty()) {
            throw new IllegalArgumentException(
                    "the table " + tableName + " has no key: the elements need one to belong to");
        }
        List<String> wanted = List.of(columns);
        List<View> views = new ArrayList<>();
        for (Column column : table.getColumns()) {
            if (!wanted.isEmpty() && !wanted.contains(column.getName())) {
                continue;
            }
            TypeDescriptor type = PARSER.parse(column);
            if (!CoreTypeFamily.ARRAY.equals(type.getTypeFamily()) || !flat(type.element())) {
                if (!wanted.isEmpty()) {
                    throw new IllegalArgumentException(
                            "the column "
                                    + column.getName()
                                    + " is not a list of structs or scalars: "
                                    + type.portable());
                }
                continue;
            }
            views.add(
                    view(
                            table,
                            keys,
                            column.getName(),
                            type.element(),
                            sql(
                                    dialect,
                                    table.getName() + "_" + column.getName(),
                                    table.getName(),
                                    keys,
                                    column.getName(),
                                    type.element(),
                                    fieldsOf(type.element()))));
        }
        return views;
    }

    /**
     * The view of an array inside a JSON column, for the dialects that have JSON and no lists. The
     * catalog declares the array and its elements in the {@code nested} of the column ({@code
     * .a.b[] : INTEGER}); {@code path} names it, {@code .a.b} or {@code .a.b[]}. The view is called
     * {@code <table>_<column>_<fields joined by _>} and, like a list view, has the key, {@code
     * ordinal} and {@code value} or the fields of the elements, which are scalars.
     */
    public static View ofJson(
            Schema db, String tableName, Dialect dialect, String columnName, String path) {
        Table table =
                db.getTable(tableName)
                        .orElseThrow(() -> new IllegalArgumentException("no table " + tableName));
        List<Column> keys =
                table.getColumns().stream()
                        .filter(c -> c.getPkPos() > 0)
                        .sorted(Comparator.comparingInt(Column::getPkPos))
                        .toList();
        if (keys.isEmpty()) {
            throw new IllegalArgumentException(
                    "the table " + tableName + " has no key: the elements need one to belong to");
        }
        Column column =
                table.getColumns().stream()
                        .filter(c -> c.getName().equals(columnName))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "no column " + tableName + "." + columnName));
        TypeDescriptor type = PARSER.parse(column);
        if (type.shape() == null) {
            throw new IllegalArgumentException(
                    "the column " + columnName + " declares no members of its JSON");
        }
        String bare = path.endsWith("[]") ? path.substring(0, path.length() - 2) : path;
        TypeDescriptor array = ai.koryki.catalog.types.TypePath.parse(bare).get(type.shape());
        if (!CoreTypeFamily.ARRAY.equals(array.getTypeFamily())
                || array.element().isComplex() && !struct(array.element())) {
            throw new IllegalArgumentException(
                    bare + " is " + array.portable() + ", not a list of scalars or objects");
        }
        List<String> names = new ArrayList<>();
        StringBuilder jsonPath = new StringBuilder("$");
        for (String step : bare.substring(1).split("\\.")) {
            if (!PLAIN.matcher(step).matches()) {
                throw new IllegalArgumentException("the member " + step + " has no plain name");
            }
            names.add(step);
            jsonPath.append('.').append(step);
        }
        String source = columnName + "_" + String.join("_", names);
        TypeDescriptor element = array.element();
        return view(
                table,
                keys,
                source,
                element,
                jsonSql(
                        dialect,
                        table.getName() + "_" + source,
                        table.getName(),
                        keys,
                        columnName,
                        jsonPath.toString(),
                        element));
    }

    private static List<String> fieldsOf(TypeDescriptor element) {
        return struct(element) ? element.fieldNames() : List.of("value");
    }

    private static boolean flat(TypeDescriptor element) {
        return !CoreTypeFamily.ARRAY.equals(element.getTypeFamily())
                && !CoreTypeFamily.MAP.equals(element.getTypeFamily());
    }

    private static boolean struct(TypeDescriptor element) {
        return CoreTypeFamily.STRUCT.equals(element.getTypeFamily());
    }

    private static View view(
            Table table, List<Column> keys, String source, TypeDescriptor element, String sql) {
        String name = table.getName() + "_" + source;
        List<String> fields = fieldsOf(element);

        Table viewTable = new Table();
        viewTable.setName(name);
        viewTable.setLabel(label(name));
        viewTable.setDescription(
                "One row per element of the list "
                        + table.getName()
                        + "."
                        + source
                        + ", with its position in it.");
        List<Column> columns = new ArrayList<>();
        for (Column key : keys) {
            Column copy = new Column(key.getName());
            copy.setLabel(key.getLabel());
            copy.setDescription("Key of the row the element belongs to");
            copy.setTypeFamily(key.getTypeFamily());
            copy.setTypeEncoding(key.getTypeEncoding());
            copy.setDialectType(key.getDialectType());
            copy.setNullable(false);
            copy.setPkPos(key.getPkPos());
            columns.add(copy);
        }
        Column ordinal = new Column("ordinal");
        ordinal.setLabel("Ordinal");
        ordinal.setDescription("Position of the element in the list, from 1");
        ordinal.setTypeFamily("INTEGER");
        ordinal.setDialectType("INTEGER");
        ordinal.setNullable(false);
        ordinal.setPkPos(keys.size() + 1);
        columns.add(ordinal);
        for (int i = 0; i < fields.size(); i++) {
            TypeDescriptor fieldType = struct(element) ? element.children().get(i) : element;
            Column field = new Column(fields.get(i));
            field.setLabel(label(fields.get(i)));
            field.setDescription(
                    struct(element)
                            ? "The field " + fields.get(i) + " of the element"
                            : "The element");
            field.setTypeFamily(fieldType.getTypeFamily().name());
            field.setDialectType(fieldType.getPhysicalTypeName());
            field.setNullable(true);
            columns.add(field);
        }
        viewTable.setColumns(columns);

        String relationName = source + "_of_" + table.getName();
        Relation relation = new Relation(relationName);
        relation.setComment("the elements of the list " + source);
        relation.setStartTable(name);
        relation.setEndTable(table.getName());
        relation.setSymmetric(false);
        relation.setStartColumns(keys.stream().map(Column::getName).toList());
        relation.setEndColumns(keys.stream().map(Column::getName).toList());

        Entity entity = new Entity(name);
        entity.setLabel(label(name));
        entity.setDescription(viewTable.getDescription());
        List<Attribute> attributes = new ArrayList<>();
        for (Column c : columns) {
            Attribute attribute = new Attribute(c.getName());
            attribute.setLabel(c.getLabel());
            attribute.setDescription(c.getDescription());
            attributes.add(attribute);
        }
        entity.setAttributes(attributes);

        Link link = new Link();
        link.setName(relationName);
        link.setComment("the elements of the list " + source + " of a row");
        link.setRelations(new ArrayList<>(List.of(relationName)));
        return new View(name, sql, viewTable, relation, entity, link);
    }

    /** The views added to a catalog and its model, which are changed. */
    public static void merge(Schema db, Model model, List<View> views) {
        for (View v : views) {
            db.getTables().removeIf(t -> t.getName().equals(v.name()));
            db.getRelations().removeIf(r -> r.getName().equals(v.relation().getName()));
            db.addTable(v.table());
            db.addRelation(v.relation());
            model.getEntities().removeIf(e -> e.getName().equals(v.name()));
            model.getLinks().removeIf(l -> l.getName().equals(v.link().getName()));
            model.getEntities().add(v.entity());
            model.getLinks().add(v.link());
        }
    }

    // ---- SQL -----------------------------------------------------------------------------------

    private static String sql(
            Dialect dialect,
            String view,
            String table,
            List<Column> keys,
            String column,
            TypeDescriptor element,
            List<String> fields) {
        String parentKeys =
                keys.stream().map(k -> "t." + id(k.getName())).collect(Collectors.joining(", "));
        boolean struct = struct(element);
        String list = "t." + id(column);
        String head = "CREATE OR REPLACE VIEW " + view + " AS\nSELECT " + parentKeys + ", ";
        String listed = fields.stream().map(FlattenViews::id).collect(Collectors.joining(", "));
        switch (dialect) {
            case DUCKDB:
                return head
                        + "u.ordinal, "
                        + fields.stream()
                                .map(
                                        f ->
                                                struct
                                                        ? "u.item." + id(f) + " AS " + id(f)
                                                        : "u.item AS value")
                                .collect(Collectors.joining(", "))
                        + "\nFROM "
                        + table
                        + " t, UNNEST("
                        + list
                        + ") WITH ORDINALITY AS u(item, ordinal);";
            case POSTGRESQL:
            case TRINO:
                // both spread the fields of a composite or a row into columns of their own, so the
                // alias list names them: one for each field, then the position
                return head
                        + "u.ordinal, "
                        + fields.stream().map(f -> "u." + id(f)).collect(Collectors.joining(", "))
                        + "\nFROM "
                        + table
                        + " t CROSS JOIN "
                        + (dialect == Dialect.POSTGRESQL ? "LATERAL " : "")
                        + (dialect == Dialect.POSTGRESQL ? "unnest(" : "UNNEST(")
                        + list
                        + ") WITH ORDINALITY AS u("
                        + listed
                        + ", ordinal);";
            case SNOWFLAKE:
                return head
                        + "f.index + 1 AS ordinal, "
                        + (struct
                                ? fields.stream()
                                        .map(
                                                f ->
                                                        "f.value:"
                                                                + id(f)
                                                                + "::"
                                                                + snowflakeType(
                                                                        element.field(f)
                                                                                .orElseThrow())
                                                                + " AS "
                                                                + id(f))
                                        .collect(Collectors.joining(", "))
                                : "f.value::" + snowflakeType(element) + " AS value")
                        + "\nFROM "
                        + table
                        + " t, LATERAL FLATTEN(INPUT => "
                        + list
                        + ") f;";
            case ORACLE:
                String partition =
                        keys.stream()
                                .map(k -> "t." + id(k.getName()))
                                .collect(Collectors.joining(", "));
                return head
                        + "ROW_NUMBER() OVER (PARTITION BY "
                        + partition
                        + " ORDER BY ROWNUM) AS ordinal, "
                        + fields.stream()
                                .map(
                                        f ->
                                                struct
                                                        ? "u." + id(f) + " AS " + id(f)
                                                        : "u.COLUMN_VALUE AS value")
                                .collect(Collectors.joining(", "))
                        + "\nFROM "
                        + table
                        + " t, TABLE("
                        + list
                        + ") u;";
            default:
                throw new IllegalArgumentException("no dialect " + dialect);
        }
    }

    /**
     * The view over a JSON array: SQLite {@code json_each}, MariaDB {@code JSON_TABLE}, SQL Server
     * {@code OPENJSON}.
     */
    private static String jsonSql(
            Dialect dialect,
            String view,
            String table,
            List<Column> keys,
            String column,
            String jsonPath,
            TypeDescriptor element) {
        boolean struct = struct(element);
        List<String> fields = fieldsOf(element);
        String parentKeys =
                keys.stream().map(k -> "t." + id(k.getName())).collect(Collectors.joining(", "));
        String doc = "t." + id(column);
        switch (dialect) {
            case SQLITE:
                return "CREATE VIEW "
                        + view
                        + " AS\nSELECT "
                        + parentKeys
                        + ", j.key + 1 AS ordinal, "
                        + fields.stream()
                                .map(
                                        f ->
                                                struct
                                                        ? "json_extract(j.value, '$."
                                                                + f
                                                                + "') AS "
                                                                + id(f)
                                                        : "j.value AS value")
                                .collect(Collectors.joining(", "))
                        + "\nFROM "
                        + table
                        + " t, json_each("
                        + doc
                        + ", '"
                        + jsonPath
                        + "') j\nWHERE json_type("
                        + doc
                        + ", '"
                        + jsonPath
                        + "') = 'array';";
            case MARIADB:
                return "CREATE OR REPLACE VIEW "
                        + view
                        + " AS\nSELECT "
                        + parentKeys
                        + ", jt.ordinal, "
                        + fields.stream()
                                .map(f -> "jt." + backtick(f) + " AS " + backtick(f))
                                .collect(Collectors.joining(", "))
                        + "\nFROM "
                        + table
                        + " t, JSON_TABLE("
                        + doc
                        + ", '"
                        + jsonPath
                        + "[*]' COLUMNS (ordinal FOR ORDINALITY, "
                        + fields.stream()
                                .map(
                                        f ->
                                                backtick(f)
                                                        + " "
                                                        + jsonType(
                                                                dialect,
                                                                struct
                                                                        ? element.field(f)
                                                                                .orElseThrow()
                                                                        : element)
                                                        + " PATH '"
                                                        + (struct ? "$." + f : "$")
                                                        + "'")
                                .collect(Collectors.joining(", "))
                        + ")) jt;";
            case MSSQL:
                String array = "JSON_QUERY(" + doc + ", '" + jsonPath + "')";
                return "CREATE OR ALTER VIEW "
                        + view
                        + " AS\nSELECT "
                        + parentKeys
                        + ", CAST(j.[key] AS INT) + 1 AS ordinal, "
                        + fields.stream()
                                .map(
                                        f ->
                                                "CAST("
                                                        + (struct
                                                                ? "JSON_VALUE(j.value, '$."
                                                                        + f
                                                                        + "')"
                                                                : "j.value")
                                                        + " AS "
                                                        + jsonType(
                                                                dialect,
                                                                struct
                                                                        ? element.field(f)
                                                                                .orElseThrow()
                                                                        : element)
                                                        + ") AS ["
                                                        + f
                                                        + "]")
                                .collect(Collectors.joining(", "))
                        + "\nFROM "
                        + table
                        + " t CROSS APPLY OPENJSON(CASE WHEN LEFT("
                        + array
                        + ", 1) = '[' THEN "
                        + array
                        + " END) j;";
            default:
                throw new IllegalArgumentException(dialect + " has lists; use of()");
        }
    }

    private static String backtick(String name) {
        return "`" + name.replace("`", "``") + "`";
    }

    /** The column type of a scalar read out of JSON. */
    private static String jsonType(Dialect dialect, TypeDescriptor type) {
        var family = type.getTypeFamily();
        boolean mssql = dialect == Dialect.MSSQL;
        if (CoreTypeFamily.INTEGER.equals(family)) {
            return mssql ? "INT" : "INT";
        } else if (CoreTypeFamily.FLOAT.equals(family)) {
            return mssql ? "FLOAT" : "DOUBLE";
        } else if (CoreTypeFamily.DECIMAL.equals(family)) {
            return "DECIMAL(38, 10)";
        } else if (CoreTypeFamily.BOOLEAN.equals(family)) {
            return mssql ? "BIT" : "TINYINT(1)";
        } else if (CoreTypeFamily.TEXT.equals(family)) {
            return mssql ? "NVARCHAR(4000)" : "VARCHAR(4000)";
        }
        throw new IllegalArgumentException("a JSON member of " + type.portable() + " is not read");
    }

    /** What a field of a Snowflake variant is cast to, by what the field is. */
    private static String snowflakeType(TypeDescriptor type) {
        var family = type.getTypeFamily();
        if (CoreTypeFamily.INTEGER.equals(family)) {
            return "INTEGER";
        } else if (CoreTypeFamily.DECIMAL.equals(family)) {
            return "NUMBER("
                    + Math.max(type.getPrecision(), 1)
                    + ", "
                    + Math.max(type.getScale(), 0)
                    + ")";
        } else if (CoreTypeFamily.FLOAT.equals(family)) {
            return "DOUBLE";
        } else if (CoreTypeFamily.BOOLEAN.equals(family)) {
            return "BOOLEAN";
        } else if (CoreTypeFamily.DATE.equals(family)) {
            return "DATE";
        } else if (CoreTypeFamily.TIMESTAMP.equals(family)) {
            return "TIMESTAMP_NTZ";
        } else if (CoreTypeFamily.ARRAY.equals(family)) {
            return "ARRAY(" + snowflakeType(type.element()) + ")";
        } else if (CoreTypeFamily.TEXT.equals(family)) {
            return "VARCHAR";
        }
        return "VARIANT";
    }

    private static String id(String name) {
        return PLAIN.matcher(name).matches() ? name : "\"" + name.replace("\"", "\"\"") + "\"";
    }

    private static String label(String name) {
        return java.util.Arrays.stream(name.split("_"))
                .filter(w -> !w.isEmpty())
                .map(w -> w.substring(0, 1).toUpperCase(Locale.ROOT) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    /**
     * Writes the views of a table: {@code flatten_<dialect>.sql}, and the catalog and model with
     * the views added, to a directory.
     *
     * <pre>
     * FlattenViews dialect db.json model.json table out-directory [column | column:.json.path ...]
     * </pre>
     *
     * A {@code column:path} names an array inside a JSON column, for the dialects with JSON only.
     */
    public static void main(String[] args) throws java.io.IOException {
        if (args.length < 5) {
            System.err.println(
                    "FlattenViews dialect db.json model.json table out-directory [column ...]");
            System.exit(2);
        }
        Dialect dialect = Dialect.valueOf(args[0].toUpperCase(Locale.ROOT));
        tools.jackson.databind.ObjectMapper mapper =
                tools.jackson.databind.json.JsonMapper.builder()
                        .addMixIn(Column.class, TreeProperties.class)
                        .build();
        Schema db = mapper.readValue(new java.io.File(args[1]), Schema.class);
        Model model = mapper.readValue(new java.io.File(args[2]), Model.class);
        List<View> views = new ArrayList<>();
        List<String> lists = new ArrayList<>();
        for (String c : java.util.Arrays.copyOfRange(args, 5, args.length)) {
            int colon = c.indexOf(':');
            if (colon > 0) {
                views.add(
                        ofJson(
                                db,
                                args[3],
                                dialect,
                                c.substring(0, colon),
                                c.substring(colon + 1)));
            } else {
                lists.add(c);
            }
        }
        if (views.isEmpty() || !lists.isEmpty()) {
            views.addAll(of(db, args[3], dialect, lists.toArray(new String[0])));
        }
        merge(db, model, views);

        java.nio.file.Path out = java.nio.file.Path.of(args[4]);
        java.nio.file.Files.createDirectories(out);
        java.nio.file.Files.writeString(
                out.resolve("flatten_" + dialect.name().toLowerCase(Locale.ROOT) + ".sql"),
                views.stream().map(View::sql).collect(Collectors.joining("\n\n")) + "\n");
        mapper.writerWithDefaultPrettyPrinter().writeValue(out.resolve("db.json").toFile(), db);
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(out.resolve("model.json").toFile(), model);
    }

    /**
     * How a column writes its tree: only the parts it has. The catalog classes carry no annotations
     * of their own; what a writer wants left out is said here.
     */
    abstract static class TreeProperties {
        @com.fasterxml.jackson.annotation.JsonInclude(
                com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        abstract List<Column> getFields();

        @com.fasterxml.jackson.annotation.JsonInclude(
                com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        abstract Column getElement();

        @com.fasterxml.jackson.annotation.JsonInclude(
                com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        abstract Column getKey();

        @com.fasterxml.jackson.annotation.JsonInclude(
                com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        abstract Column getValue();

        @com.fasterxml.jackson.annotation.JsonInclude(
                com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        abstract Integer getLength();
    }
}
