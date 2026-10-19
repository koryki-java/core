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
package ai.koryki.databases.complex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Table;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.databases.complex.duckdb.ComplexService;
import ai.koryki.iql.LinkResolver;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The complex catalog loads, and every nested column of it reads as the tree the table was designed
 * to be. {@link LinkResolver} parses every column when it is built, so one type it cannot read
 * fails the whole resolver, and that is the first thing this checks.
 */
class ComplexServiceTest {

    private static final Map<String, String> EXPECTED =
            Map.ofEntries(
                    Map.entry("nr", "INTEGER"),
                    Map.entry("tags", "ARRAY(TEXT)"),
                    Map.entry("scores", "ARRAY(INTEGER)"),
                    Map.entry("vec", "ARRAY(FLOAT,3)"),
                    Map.entry("matrix", "ARRAY(ARRAY(FLOAT,3),2)"),
                    Map.entry("dims", "STRUCT(w:FLOAT,h:FLOAT)"),
                    Map.entry(
                            "address",
                            "STRUCT(street:TEXT,geo:STRUCT(lat:FLOAT,lon:FLOAT),phones:ARRAY(TEXT))"),
                    Map.entry("attrs", "MAP(TEXT,TEXT)"),
                    Map.entry("counts", "MAP(TEXT,INTEGER)"),
                    Map.entry("lookup", "MAP(INTEGER,TEXT)"),
                    Map.entry(
                            "items",
                            "ARRAY(STRUCT(sku:TEXT,qty:INTEGER,price:DECIMAL,opts:ARRAY(TEXT)))"),
                    Map.entry("events", "ARRAY(STRUCT(ts:TIMESTAMP,day:DATE))"),
                    Map.entry(
                            "deep",
                            "ARRAY(MAP(TEXT,STRUCT(a:ARRAY(INTEGER),b:MAP(INTEGER,ARRAY(TEXT)))))"),
                    Map.entry(
                            "doc",
                            "JSON(STRUCT(n:INTEGER,a:STRUCT(b:ARRAY(INTEGER)),s:TEXT,b:BOOLEAN,d:DECIMAL))"),
                    Map.entry("odd", "STRUCT(Odd Name:INTEGER,x:INTEGER)"),
                    Map.entry(
                            "branches",
                            "ARRAY(STRUCT(street:TEXT,city:TEXT,zip:TEXT,geo:STRUCT(lat:FLOAT,lon:FLOAT)))"),
                    Map.entry(
                            "branches_json",
                            "JSON(ARRAY(STRUCT(street:TEXT,city:TEXT,zip:TEXT,geo:STRUCT(lat:FLOAT,lon:FLOAT))))"));

    @Test
    void theResolverLoadsInStrictMode() {
        LinkResolver resolver = ComplexService.resolver();

        assertNotNull(resolver);
        assertTrue(resolver.isStrict());
        assertTrue(resolver.isEntity("check_complex"));
        assertTrue(resolver.getLinkMap().containsKey("same_check"));
    }

    @Test
    void everyColumnIsDescribedAsDesigned() {
        LinkResolver resolver = ComplexService.resolver();
        Table table = resolver.getSchema().getTables().get(0);

        assertEquals("check_complex", table.getName());
        assertEquals(
                EXPECTED.keySet(),
                table.getColumns().stream()
                        .map(Column::getName)
                        .collect(java.util.stream.Collectors.toSet()));
        for (Column column : table.getColumns()) {
            TypeDescriptor type = resolver.getTypeDescriptor(column);
            assertEquals(EXPECTED.get(column.getName()), type.portable(), column.getName());
        }
    }

    @Test
    void theDeclaredFamilyIsTheFamilyOfTheRoot() {
        LinkResolver resolver = ComplexService.resolver();

        for (Column column : resolver.getSchema().getTables().get(0).getColumns()) {
            ai.koryki.catalog.schema.Concept concept = column.getConcept();
            assertEquals(
                    "JSON".equals(concept.getTypeEncoding())
                            ? CoreTypeFamily.JSON
                            : CoreTypeFamily.valueOf(concept.getTypeFamily()),
                    resolver.getTypeDescriptor(column).getTypeFamily(),
                    column.getName());
        }
    }

    @Test
    void theFlattenViewsAreEntitiesLinkedToTheirParent() {
        LinkResolver resolver = ComplexService.resolver();

        for (String view : new String[] {"tags", "items", "events"}) {
            assertTrue(resolver.isEntity("check_complex_" + view), view);
            assertTrue(resolver.getLinkMap().containsKey(view + "_of_check_complex"), view);
        }
        Table items =
                resolver.getSchema().getTables().stream()
                        .filter(t -> t.getName().equals("check_complex_items"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                java.util.List.of("nr", "ordinal", "sku", "qty", "price", "opts"),
                items.getColumns().stream().map(Column::getName).toList());
    }
}
