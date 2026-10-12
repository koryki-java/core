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
package ai.koryki.snowflake.complex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.iql.LinkResolver;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The Snowflake catalog of the complex-types test bed loads, and every column reads as the tree the
 * table was designed to be: the same trees as DuckDB's, except that Snowflake has no fixed-size
 * array. No connection is needed: the resolver parses every column when it is built, so one type it
 * cannot read fails here.
 */
class ComplexCatalogTest {

    public static final String DB = "/ai/koryki/snowflake/databases/complex";
    public static final String MODEL = "/ai/koryki/snowflake/databases/complex/model";

    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();

    static {
        EXPECTED.put("nr", "INTEGER");
        EXPECTED.put("tags", "ARRAY(TEXT)");
        EXPECTED.put("scores", "ARRAY(INTEGER)");
        EXPECTED.put("vec", "ARRAY(FLOAT)");
        EXPECTED.put("matrix", "ARRAY(ARRAY(FLOAT))");
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
        EXPECTED.put("doc", "JSON(STRUCT(n:INTEGER,a:STRUCT(b:ARRAY(INTEGER))))");
        EXPECTED.put("odd", "STRUCT(Odd Name:INTEGER,x:INTEGER)");
    }

    private static LinkResolver resolver() {
        Locale locale = Locale.ENGLISH;
        Schema db = CatalogLoader.db(DB);
        Model model = CatalogLoader.model(MODEL, locale);
        return new LinkResolver(locale, db, model, true);
    }

    @Test
    void theResolverLoadsInStrictMode() {
        LinkResolver resolver = resolver();

        assertTrue(resolver.isStrict());
        assertTrue(resolver.isEntity("check_complex"));
        assertTrue(resolver.getLinkMap().containsKey("same_check"));
    }

    @Test
    void everyColumnIsDescribedAsDesigned() {
        LinkResolver resolver = resolver();

        Column[] columns =
                resolver.getSchema().getTables().get(0).getColumns().toArray(new Column[0]);
        assertEquals(EXPECTED.size(), columns.length);
        for (Column column : columns) {
            assertEquals(
                    EXPECTED.get(column.getName()),
                    resolver.getTypeDescriptor(column).portable(),
                    column.getName());
            assertEquals(
                    CoreTypeFamily.valueOf(column.getTypeFamily()),
                    resolver.getTypeDescriptor(column).getTypeFamily(),
                    column.getName());
        }
    }
}
