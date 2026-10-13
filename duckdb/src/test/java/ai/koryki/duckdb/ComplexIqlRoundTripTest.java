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

import ai.koryki.databases.cases.Fixtures;
import ai.koryki.databases.complex.duckdb.ComplexService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.IQLSerializer;
import ai.koryki.iql.IQLTranspiler;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.kql.KQLTranspiler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * IQL is the second front door, and the form a query is stored and exchanged in: every complex
 * query, written out as IQL and read back, has to render the SQL it rendered from KQL. Selectors,
 * lambdas and their parameters are the parts the older corpus has never carried through it.
 *
 * <p>One difference is accepted and normalised away: IQL writes the list of {@code x IN list} in
 * brackets, which come back as a grouping and appear around a plain reference in the SQL.
 */
class ComplexIqlRoundTripTest {

    static List<Path> fixtures() throws IOException {
        try (Stream<Path> walk = Files.walk(Fixtures.queries("complex"))) {
            return walk.filter(f -> f.toString().endsWith(".kql")).sorted().toList();
        }
    }

    private static String sql(KQLTranspiler t) {
        return t.getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));
    }

    /** The brackets IN puts around a reference are not part of what the SQL says. */
    private static String normalised(String sql) {
        return sql.strip().replaceAll("\\((\\w+\\.\\w+)\\)", "$1");
    }

    @Test
    void thereAreFixtures() throws IOException {
        assertFalse(fixtures().isEmpty(), "is -Dtest.root set?");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void aQueryWrittenAsIqlAndReadBackRendersTheSame(Path fixture) throws IOException {
        KQLTranspiler kql =
                KQLTranspiler.builder(Files.readString(fixture), ComplexService.resolver())
                        .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer())
                        .build();
        String direct = sql(kql);

        String iql = new IQLSerializer(kql.getQuery()).toString();
        String back =
                IQLTranspiler.builder(iql, ComplexService.resolver())
                        .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer())
                        .build()
                        .getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));

        assertEquals(normalised(direct), normalised(back), iql);
    }
}
