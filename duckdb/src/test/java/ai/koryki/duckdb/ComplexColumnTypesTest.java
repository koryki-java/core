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
package ai.koryki.duckdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeDescriptorParser;
import ai.koryki.catalog.types.TypeSyntax;
import ai.koryki.databases.northwind.duckdb.NorthwindDuckdb;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The proof that the type parser fits what DuckDB itself says: every column of {@code
 * check_complex} is read from {@code duckdb_columns()} with the type text DuckDB prints, and has to
 * parse, to the tree the table was designed to have, and to survive its own spelling. No catalog is
 * involved, so this runs before there is one.
 */
class ComplexColumnTypesTest {

    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();

    static {
        EXPECTED.put("nr", "INTEGER");
        EXPECTED.put("tags", "ARRAY(TEXT)");
        EXPECTED.put("scores", "ARRAY(INTEGER)");
        EXPECTED.put("vec", "ARRAY(FLOAT,3)");
        EXPECTED.put("matrix", "ARRAY(ARRAY(FLOAT,3),2)");
        EXPECTED.put("dims", "STRUCT(w:FLOAT,h:FLOAT)");
        EXPECTED.put(
                "address",
                "STRUCT(street:TEXT,geo:STRUCT(lat:FLOAT,lon:FLOAT),phones:ARRAY(TEXT))");
        EXPECTED.put("attrs", "MAP(TEXT,TEXT)");
        EXPECTED.put("counts", "MAP(TEXT,INTEGER)");
        EXPECTED.put("lookup", "MAP(INTEGER,TEXT)");
        EXPECTED.put("items", "ARRAY(STRUCT(sku:TEXT,qty:INTEGER,price:DECIMAL,opts:ARRAY(TEXT)))");
        EXPECTED.put("events", "ARRAY(STRUCT(ts:TIMESTAMP,day:DATE))");
        EXPECTED.put(
                "deep", "ARRAY(MAP(TEXT,STRUCT(a:ARRAY(INTEGER),b:MAP(INTEGER,ARRAY(TEXT)))))");
        EXPECTED.put("doc", "JSON");
        EXPECTED.put("odd", "STRUCT(Odd Name:INTEGER,x:INTEGER)");
        EXPECTED.put(
                "branches",
                "ARRAY(STRUCT(street:TEXT,city:TEXT,zip:TEXT,geo:STRUCT(lat:FLOAT,lon:FLOAT)))");
        EXPECTED.put("branches_json", "JSON");
    }

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    @Test
    void everyColumnOfTheTestBedParsesToItsDesignedTree() throws SQLException {
        Map<String, String> found = new LinkedHashMap<>();
        try (Connection c = NorthwindDuckdb.fromResource(NorthwindDuckdb.DUCKDB);
                Statement st = c.createStatement();
                ResultSet rs =
                        st.executeQuery(
                                "select column_name, data_type from duckdb_columns()"
                                        + " where table_name = 'check_complex'"
                                        + " order by column_index")) {
            while (rs.next()) {
                found.put(rs.getString(1), rs.getString(2));
            }
        }
        assertFalse(found.isEmpty(), "check_complex is missing from northwind.duckdb");
        assertEquals(EXPECTED.keySet(), found.keySet(), "the columns of the test bed");

        for (Map.Entry<String, String> e : found.entrySet()) {
            String dataType = e.getValue();
            TypeDescriptor d = parse(e.getKey(), dataType);

            assertEquals(EXPECTED.get(e.getKey()), d.portable(), e.getKey() + ": " + dataType);

            // and what the descriptor says about itself reads back to the same descriptor
            if (d.isComplex()) {
                assertEquals(
                        d,
                        parse(e.getKey(), d.getPhysicalTypeName()),
                        e.getKey() + ": " + d.getPhysicalTypeName());
            }
        }
    }

    @Test
    void decimalKeepsItsPrecisionDeepInsideTheNest() throws SQLException {
        TypeDescriptor items =
                parse(
                        "items",
                        "STRUCT(sku VARCHAR, qty INTEGER, price DECIMAL(10,2), opts VARCHAR[])[]");

        TypeDescriptor price = items.element().field("price").orElseThrow();
        assertEquals(10, price.getPrecision());
        assertEquals(2, price.getScale());
    }

    private static TypeDescriptor parse(String name, String dataType) {
        return TypeSyntax.looksComplex(dataType)
                ? TypeSyntax.parse(dataType, PARSER::parseLeaf)
                : PARSER.parse("column '" + name + "'", familyOf(dataType), null, dataType);
    }

    /** The family a catalog would declare: the root of the spelling. */
    private static String familyOf(String dataType) {
        String t = dataType.stripTrailing();
        if (t.endsWith("]")) {
            return "ARRAY";
        } else if (t.startsWith("STRUCT(")) {
            return "STRUCT";
        } else if (t.startsWith("MAP(")) {
            return "MAP";
        } else if (t.equals("JSON")) {
            return "JSON";
        } else if (t.startsWith("SMALLINT")) {
            return "INTEGER";
        }
        throw new IllegalArgumentException(dataType);
    }
}
