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

import ai.koryki.databases.FileAsserter;
import ai.koryki.databases.cases.Fixtures;
import ai.koryki.databases.cases.TestUtil;
import ai.koryki.databases.oraview.OraViewService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.Identifier;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class OraviewTest {

    public static final String ORAVIEW_ROOT = "src/test/resources/ai/koryki/core/kql/oraview";

    public static final String EXPECTED_SQL = "src/test/resources/ai/koryki/core/expected/kql/oraview/sql";

    public static final String SUFFIX = ".kql";

    private static LinkResolver resolver;

    @BeforeAll
    public static void readNorthwindDB() {
        resolver = OraViewService.resolver();
    }

    @Test
    public void dialect_1() throws IOException {

        Path p = Paths.get(ORAVIEW_ROOT + "/dialect_1.kql");

        InputStream kql = new FileInputStream(p.toFile());
        KQLTranspiler transpiler = KQLTranspiler.builder(kql, resolver).build();
        String sql = transpiler.getSql(new SqlQueryRenderer(Identifier.quoted, DuckdbBaseDialect.INSTANCE, java.time.ZoneId.of("UTC")));

        // Until now this test rewrote the expectation file on every run and checked nothing
        // -- it could not fail at all. It now compares like the other fixtures.
        Path expectedSql = TestUtil.expected(p, Path.of(ORAVIEW_ROOT), Path.of(EXPECTED_SQL), ".sql");
        if (expectedSql.toFile().canRead()) {
            FileAsserter.scriptAssert(Files.readString(expectedSql), sql);
        } else {
            Fixtures.writeOrFail(sql, expectedSql.toFile());
        }
    }

}
