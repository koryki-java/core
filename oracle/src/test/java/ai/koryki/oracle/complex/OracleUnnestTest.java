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

package ai.koryki.oracle.complex;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.CatalogLoader;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.KQLTranspiler;
import ai.koryki.oracle.iql.OracleDialect;
import ai.koryki.oracle.iql.SqlQueryRenderer;
import java.time.ZoneId;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * What the corpus of the schema {@code unnest} does not say for Oracle: a list of objects cannot be
 * filtered by a lambda to be unnested, because the generic collection types hold only text, numbers
 * and dates. No connection is needed.
 */
class OracleUnnestTest {

    private static final LinkResolver RESOLVER =
            new LinkResolver(
                    Locale.ENGLISH,
                    CatalogLoader.db(ComplexCatalogTest.DB),
                    CatalogLoader.model(ComplexCatalogTest.MODEL, Locale.ENGLISH),
                    true);

    private static String sql(String kql) {
        return KQLTranspiler.builder(kql, RESOLVER)
                .functions(OracleDialect.INSTANCE.getFunctionRenderer())
                .build()
                .getSql(new SqlQueryRenderer(ZoneId.of("UTC")));
    }

    @Test
    void aListOfNumbersMadeByALambdaIsUnnested() {
        String sql =
                sql(
                        "FIND check_complex t, t unnest(list_filter(t.scores, x -> x > 5)) s"
                                + " FETCH t.nr, s.value");

        assertTrue(sql.contains("CROSS APPLY TABLE("), sql);
    }

    @Test
    void aListOfObjectsMadeByALambdaIsRefused() {
        KorykiaiException e =
                assertThrows(
                        KorykiaiException.class,
                        () ->
                                sql(
                                        "FIND check_complex t,"
                                                + " t unnest(list_filter(t.items, x -> x@qty > 1)) i"
                                                + " FETCH t.nr, i.sku"));

        assertTrue(e.getMessage().contains("Oracle cannot build a list of STRUCT"), e.getMessage());
    }
}
