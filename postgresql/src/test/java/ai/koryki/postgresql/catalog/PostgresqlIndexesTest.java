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
package ai.koryki.postgresql.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.schema.Index;
import ai.koryki.catalog.schema.IndexKey;
import ai.koryki.postgresql.PostgreSQLUnavailable;
import ai.koryki.postgresql.northwind.NorthwindPostgresql;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * An index as PostgreSQL prints it, as a catalog entry. The definitions here are copied from {@code
 * pg_get_indexdef} of the complex bed, which is the only form the reader has to understand.
 */
class PostgresqlIndexesTest {

    private static Optional<Index> parse(String name, String method, String keys) {
        return PostgresqlIndexes.parse(
                name,
                method,
                false,
                null,
                "CREATE INDEX " + name + " ON public.t USING " + method + " (" + keys + ")");
    }

    @Test
    void aColumn() {
        Index i =
                PostgresqlIndexes.parse(
                                "pk",
                                "btree",
                                true,
                                null,
                                "CREATE UNIQUE INDEX pk ON public.check_complex USING btree (nr)")
                        .orElseThrow();

        assertEquals("btree", i.getMethod());
        assertEquals(true, i.getUnique());
        assertEquals("nr", i.getKeys().get(0).getColumn());
        assertFalse(i.getKeys().get(0).hasPath());
    }

    @Test
    void aColumnWithAnOperatorClass() {
        Index i = parse("g", "gin", "doc jsonb_path_ops").orElseThrow();

        assertEquals("gin", i.getMethod());
        assertEquals("jsonb_path_ops", i.getOpclass());
        assertEquals("doc", i.getKeys().get(0).getColumn());
    }

    @Test
    void aOrderingIsNotAnOperatorClass() {
        Index i = parse("o", "btree", "nr DESC").orElseThrow();

        assertEquals(null, i.getOpclass());
        assertEquals("nr", i.getKeys().get(0).getColumn());
    }

    @Test
    void oneKeyOfAJsonbColumn() {
        Index i = parse("k", "btree", "((attrs ->> 'color'::text))").orElseThrow();

        IndexKey key = i.getKeys().get(0);
        assertEquals("attrs", key.getColumn());
        assertEquals(List.of("color"), key.getPath());
        assertEquals("TEXT", key.getAs());
    }

    @Test
    void oneKeyOfAJsonbColumnCast() {
        Index i = parse("n", "btree", "(((doc ->> 'n'::text))::integer)").orElseThrow();

        IndexKey key = i.getKeys().get(0);
        assertEquals("doc", key.getColumn());
        assertEquals(List.of("n"), key.getPath());
        assertEquals("INTEGER", key.getAs());
    }

    @Test
    void severalKeys() {
        Index i = parse("m", "btree", "nr, ((attrs ->> 'a, b'::text)), doc").orElseThrow();

        assertEquals(3, i.getKeys().size());
        assertEquals(List.of("a, b"), i.getKeys().get(1).getPath());
        assertEquals("doc", i.getKeys().get(2).getColumn());
    }

    @Test
    void aQuoteInAKeyIsUndone() {
        Index i = parse("q", "btree", "((attrs ->> 'it''s'::text))").orElseThrow();

        assertEquals(List.of("it's"), i.getKeys().get(0).getPath());
    }

    @Test
    void aQuotedColumn() {
        Index i = parse("c", "btree", "\"Odd Name\"").orElseThrow();

        assertEquals("Odd Name", i.getKeys().get(0).getColumn());
    }

    @Test
    void aPartialIndexKeepsItsPredicate() {
        Index i =
                PostgresqlIndexes.parse(
                                "p",
                                "btree",
                                false,
                                "(nr > 1)",
                                "CREATE INDEX p ON public.t USING btree (nr) WHERE (nr > 1)")
                        .orElseThrow();

        assertFalse(i.coversAllRows());
        assertEquals("(nr > 1)", i.getWhere());
    }

    @Test
    void anExpressionItDoesNotUnderstandLeavesTheWholeIndexOut() {
        assertTrue(parse("e", "btree", "lower((name)::text)").isEmpty());
        assertTrue(parse("f", "btree", "nr, ((a + b))").isEmpty());
        assertTrue(parse("g", "btree", "((attrs #>> '{a,b}'::text[]))").isEmpty());
    }

    @Test
    @PostgreSQLUnavailable
    void theIndexesOfTheComplexBedAreRead() throws SQLException {
        try (Connection connection = NorthwindPostgresql.connection()) {
            List<Index> indexes = PostgresqlIndexes.read(connection, "check_complex");

            List<String> names = indexes.stream().map(Index::getName).toList();
            assertTrue(names.contains("check_complex_pkey"), names.toString());
            Index color =
                    indexes.stream()
                            .filter(i -> i.getName().equals("idx_check_complex_attrs_color"))
                            .findFirst()
                            .orElseThrow();
            assertEquals(List.of("color"), color.getKeys().get(0).getPath());
            Index docGin =
                    indexes.stream()
                            .filter(i -> i.getName().equals("idx_check_complex_doc_gin"))
                            .findFirst()
                            .orElseThrow();
            assertEquals("jsonb_path_ops", docGin.getOpclass());
        }
    }
}
