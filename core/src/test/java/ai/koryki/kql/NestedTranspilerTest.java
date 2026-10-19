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

package ai.koryki.kql;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.antlr.Text;
import ai.koryki.databases.FileAsserter;
import ai.koryki.databases.cases.Fixtures;
import ai.koryki.databases.cases.TestUtil;
import ai.koryki.databases.complex.duckdb.ComplexService;
import ai.koryki.iql.*;
import ai.koryki.iql.query.Query;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The shared fixtures of the schema {@code nested}, from KQL to the reference SQL (DuckDB's base
 * dialect, in UTC) and to IQL and back, over the branches of the complex bed; the queries that must
 * be refused are the ones whose name starts with {@code invalid}. What the engines answer is for the
 * engine tests of each dialect.
 */
public class NestedTranspilerTest {

    public static final Path SHARED = Fixtures.queries("nested");
    public static final Path EXPECTED_SQL = Fixtures.expected("kql", "nested").resolve("sql");
    public static final Path EXPECTED_IQL = Fixtures.expected("kql", "nested").resolve("iql");
    public static final String SUFFIX = ".kql";

    private static LinkResolver resolver;

    @BeforeAll
    public static void readComplexBed() {
        resolver = ComplexService.resolver();
    }

    static Stream<Path> testFiles() throws IOException {
        return Files.walk(SHARED, FileVisitOption.FOLLOW_LINKS)
                .filter(p -> p.toString().endsWith(SUFFIX));
    }

    private static SqlQueryRenderer renderer() {
        return new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, java.time.ZoneId.of("UTC"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testFiles")
    void testEachFile(Path kql) throws IOException {
        KQLTranspiler transpiler =
                KQLTranspiler.builder(new FileInputStream(kql.toFile()), resolver)
                        .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer())
                        .build();

        if (kql.getFileName().toString().startsWith("invalid")) {
            // refused somewhere between the grammar and the validators, never rendered
            assertThrows(KorykiaiException.class, () -> transpiler.getSql(renderer()));
            return;
        }

        String sql = ignoreSkip(transpiler.getSql(renderer()));
        checkSql(kql, sql, transpiler);
        checkIql(kql, transpiler.getQuery());
    }

    private static void checkSql(Path kql, String sql, KQLTranspiler transpiler)
            throws IOException {
        Path expected = TestUtil.expected(kql, SHARED, EXPECTED_SQL, ".sql");
        File expectedFile = expected.toFile();
        if (expectedFile.canRead()) {
            FileAsserter.scriptAssert(Files.readString(expected), sql);
            roundtrip(transpiler, sql);
        } else {
            Fixtures.writeOrFail(sql, expectedFile);
        }
    }

    private static void checkIql(Path kql, Query query) throws IOException {
        Path iql = TestUtil.expected(kql, SHARED, EXPECTED_IQL, ".iql");
        String written = ignoreSkip(new IQLSerializer(query).toString());
        if (iql.toFile().canRead()) {
            String content = Files.readString(iql);
            FileAsserter.scriptAssert(content, written);
            IQLReader reader = new IQLReader(content, true);
            assertNotNull(reader.getCtx());
            assertNotNull(new IQLQueryMapper(reader.getCtx(), "").toScript());
        } else {
            Fixtures.writeOrFail(written, iql.toFile());
        }
    }

    private static String ignoreSkip(String s) {
        return s.lines()
                .filter(line -> !line.startsWith("-- ignore="))
                .filter(line -> !line.startsWith("// ignore="))
                .collect(Collectors.joining(Text.NL));
    }

    /** The query written as KQL again gives the same SQL. */
    private static void roundtrip(KQLTranspiler transpiler, String sql) {
        String kql = new KQLFormatter(transpiler.getCtx(), transpiler.getDescription()).format();
        String again =
                ignoreSkip(
                        KQLTranspiler.builder(kql, resolver)
                                .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer())
                                .build()
                                .getSql(renderer()));
        FileAsserter.scriptAssert(sql, again);
    }
}
