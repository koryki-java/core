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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.tools.flatten.FlattenViews.Dialect;
import ai.koryki.tools.flatten.FlattenViews.View;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The views, and what describes them, from a catalog with a list of structs and a list of texts.
 */
class FlattenViewsTest {

    private static Column column(String name, String family, String dialectType, int pk) {
        Column c = new Column(name);
        c.setTypeFamily(family);
        c.setDialectType(dialectType);
        c.setPkPos(pk);
        return c;
    }

    private static Schema db() {
        Table things = new Table("things");
        things.setColumns(
                new ArrayList<>(
                        List.of(
                                column("id", "INTEGER", "INTEGER", 1),
                                column(
                                        "items",
                                        "ARRAY",
                                        "STRUCT(sku VARCHAR, qty INTEGER, price DECIMAL(10,2))[]",
                                        0),
                                column("tags", "ARRAY", "VARCHAR[]", 0),
                                column("matrix", "ARRAY", "INTEGER[][]", 0),
                                column("title", "TEXT", "VARCHAR", 0))));
        Schema schema = new Schema("things");
        schema.addTable(things);
        return schema;
    }

    private static View view(Dialect dialect, String column) {
        return FlattenViews.of(db(), "things", dialect, column).get(0);
    }

    @Test
    void everyListOfStructsOrScalarsIsAView() {
        List<View> views = FlattenViews.of(db(), "things", Dialect.DUCKDB);

        assertEquals(
                List.of("things_items", "things_tags"), views.stream().map(View::name).toList());
    }

    @Test
    void duckdbUnnestsWithTheOrdinality() {
        String sql = view(Dialect.DUCKDB, "items").sql();

        assertTrue(sql.contains("CREATE OR REPLACE VIEW things_items AS"), sql);
        assertTrue(
                sql.contains("u.item.sku AS sku, u.item.qty AS qty, u.item.price AS price"), sql);
        assertTrue(
                sql.contains("FROM things t, UNNEST(t.items) WITH ORDINALITY AS u(item, ordinal)"),
                sql);
    }

    @Test
    void postgresqlNamesTheSpreadFields() {
        String sql = view(Dialect.POSTGRESQL, "items").sql();

        assertTrue(
                sql.contains(
                        "CROSS JOIN LATERAL unnest(t.items) WITH ORDINALITY AS u(sku, qty, price, ordinal)"),
                sql);
    }

    @Test
    void trinoNamesTheSpreadFieldsToo() {
        String sql = view(Dialect.TRINO, "tags").sql();

        assertTrue(
                sql.contains("CROSS JOIN UNNEST(t.tags) WITH ORDINALITY AS u(value, ordinal)"),
                sql);
    }

    @Test
    void snowflakeFlattensAndCastsEachField() {
        String sql = view(Dialect.SNOWFLAKE, "items").sql();

        assertTrue(sql.contains("f.index + 1 AS ordinal"), sql);
        assertTrue(sql.contains("f.value:sku::VARCHAR AS sku"), sql);
        assertTrue(sql.contains("f.value:price::NUMBER(10, 2) AS price"), sql);
        assertTrue(sql.contains("LATERAL FLATTEN(INPUT => t.items) f"), sql);
    }

    @Test
    void oracleNumbersTheRowsOfTheTable() {
        String sql = view(Dialect.ORACLE, "items").sql();

        assertTrue(
                sql.contains("ROW_NUMBER() OVER (PARTITION BY t.id ORDER BY ROWNUM) AS ordinal"),
                sql);
        assertTrue(sql.contains("FROM things t, TABLE(t.items) u"), sql);
        assertTrue(view(Dialect.ORACLE, "tags").sql().contains("u.COLUMN_VALUE AS value"));
    }

    @Test
    void theViewIsATableWithTheKeyThePositionAndTheFields() {
        Table table = view(Dialect.DUCKDB, "items").table();

        assertEquals(
                List.of("id", "ordinal", "sku", "qty", "price"),
                table.getColumns().stream().map(Column::getName).toList());
        assertEquals(1, table.getColumns().get(0).getPkPos());
        assertEquals(2, table.getColumns().get(1).getPkPos());
        assertEquals("DECIMAL(10,2)", table.getColumns().get(4).getDialectType());
        assertEquals("DECIMAL", table.getColumns().get(4).getTypeFamily());
    }

    @Test
    void aListOfScalarsHasOneColumnForTheElement() {
        Table table = view(Dialect.POSTGRESQL, "tags").table();

        assertEquals(
                List.of("id", "ordinal", "value"),
                table.getColumns().stream().map(Column::getName).toList());
        assertEquals("TEXT", table.getColumns().get(2).getTypeFamily());
    }

    @Test
    void theRelationLeadsFromTheViewToTheRowItComesFrom() {
        View v = view(Dialect.DUCKDB, "items");

        assertEquals("items_of_things", v.relation().getName());
        assertEquals("things_items", v.relation().getStartTable());
        assertEquals("things", v.relation().getEndTable());
        assertEquals(List.of("id"), v.relation().getStartColumns());
        assertEquals(List.of("id"), v.relation().getEndColumns());
        assertEquals(List.of("items_of_things"), v.link().getRelations());
    }

    @Test
    void mergedIntoACatalogAndItsModel() {
        Schema db = db();
        Model model = new Model();
        model.setEntities(new ArrayList<>());
        model.setLinks(new ArrayList<>());

        FlattenViews.merge(db, model, FlattenViews.of(db, "things", Dialect.DUCKDB, "items"));
        FlattenViews.merge(db, model, FlattenViews.of(db, "things", Dialect.DUCKDB, "items"));

        assertTrue(db.getTable("things_items").isPresent());
        assertEquals(1, db.getRelations().size());
        assertEquals(1, model.getEntities().size());
        assertEquals("Things Items", model.getEntities().get(0).getLabel());
        assertEquals(5, model.getEntities().get(0).getAttributes().size());
        assertEquals(1, model.getLinks().size());
    }

    @Test
    void aColumnThatIsNoListOfStructsOrScalarsIsRefusedWhenAsked() {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> FlattenViews.of(db(), "things", Dialect.DUCKDB, "matrix"));

        assertTrue(e.getMessage().contains("not a list of structs or scalars"), e.getMessage());
    }

    @Test
    void aTableWithoutAKeyIsRefused() {
        Schema schema = new Schema("s");
        Table table = new Table("t");
        table.setColumns(new ArrayList<>(List.of(column("xs", "ARRAY", "INTEGER[]", 0))));
        schema.addTable(table);

        assertThrows(
                IllegalArgumentException.class, () -> FlattenViews.of(schema, "t", Dialect.DUCKDB));
    }

    private static Schema jsonDb() {
        Table docs = new Table("docs");
        Column doc = column("doc", "JSON", "JSON", 0);
        Column b = column(null, "ARRAY", null, 0);
        b.setName("b");
        b.setElement(column(null, "INTEGER", null, 0));
        Column a = column(null, "STRUCT", null, 0);
        a.setName("a");
        a.setFields(List.of(b));
        Column row = column(null, "STRUCT", null, 0);
        row.setFields(List.of(column("sku", "TEXT", null, 0), column("qty", "INTEGER", null, 0)));
        Column rows = column(null, "ARRAY", null, 0);
        rows.setName("rows");
        rows.setElement(row);
        doc.setFields(List.of(a, rows));
        docs.setColumns(new ArrayList<>(List.of(column("id", "INTEGER", "INTEGER", 1), doc)));
        Schema schema = new Schema("docs");
        schema.addTable(docs);
        return schema;
    }

    @Test
    void sqliteReadsAJsonArrayWithJsonEach() {
        View v = FlattenViews.ofJson(jsonDb(), "docs", Dialect.SQLITE, "doc", ".a.b");

        assertEquals("docs_doc_a_b", v.name());
        assertTrue(v.sql().contains("j.key + 1 AS ordinal, j.value AS value"), v.sql());
        assertTrue(v.sql().contains("json_each(t.doc, '$.a.b') j"), v.sql());
        assertTrue(v.sql().contains("json_type(t.doc, '$.a.b') = 'array'"), v.sql());
        assertEquals("doc_a_b_of_docs", v.relation().getName());
    }

    @Test
    void mariadbReadsAJsonArrayWithJsonTable() {
        View v = FlattenViews.ofJson(jsonDb(), "docs", Dialect.MARIADB, "doc", ".a.b[]");

        assertTrue(
                v.sql()
                        .contains(
                                "JSON_TABLE(t.doc, '$.a.b[*]' COLUMNS (ordinal FOR ORDINALITY, `value` INT PATH '$'))"),
                v.sql());
    }

    @Test
    void sqlServerReadsAJsonArrayWithOpenjsonAndOnlyOfAnArray() {
        View v = FlattenViews.ofJson(jsonDb(), "docs", Dialect.MSSQL, "doc", ".a.b");

        assertTrue(v.sql().contains("CREATE OR ALTER VIEW docs_doc_a_b AS"), v.sql());
        assertTrue(v.sql().contains("CAST(j.[key] AS INT) + 1 AS ordinal"), v.sql());
        assertTrue(
                v.sql()
                        .contains(
                                "CROSS APPLY OPENJSON(CASE WHEN LEFT(JSON_QUERY(t.doc, '$.a.b'), 1) = '['"),
                v.sql());
    }

    @Test
    void anArrayOfObjectsHasAColumnForEachMember() {
        View v = FlattenViews.ofJson(jsonDb(), "docs", Dialect.SQLITE, "doc", ".rows");

        assertTrue(
                v.sql()
                        .contains(
                                "json_extract(j.value, '$.sku') AS sku, json_extract(j.value, '$.qty') AS qty"),
                v.sql());
        assertEquals(
                List.of("id", "ordinal", "sku", "qty"),
                v.table().getColumns().stream().map(Column::getName).toList());
    }

    @Test
    void aPathThatIsNoArrayOrAColumnWithoutMembersIsRefused() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FlattenViews.ofJson(jsonDb(), "docs", Dialect.SQLITE, "doc", ".a"));
        assertThrows(
                IllegalArgumentException.class,
                () -> FlattenViews.ofJson(db(), "things", Dialect.SQLITE, "title", ".a"));
    }
}
