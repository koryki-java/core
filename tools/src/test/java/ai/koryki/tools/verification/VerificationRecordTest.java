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

package ai.koryki.tools.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.tools.verification.VerificationRecord.Info;
import ai.koryki.tools.verification.VerificationRecord.Module;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerificationRecordTest {

    @TempDir Path root;

    private void results(String module, String suites) throws Exception {
        Path dir = root.resolve(module + "/build/test-results/test");
        Files.createDirectories(dir);
        int i = 0;
        for (String attributes : suites.split(";")) {
            Files.writeString(
                    dir.resolve("TEST-" + i++ + ".xml"),
                    "<?xml version=\"1.0\"?><testsuite name=\"x\" "
                            + attributes
                            + " time=\"1\"></testsuite>");
        }
    }

    private void database(String module, String version) throws Exception {
        Path dir = root.resolve(module + "/build/verification");
        Files.createDirectories(dir);
        Files.writeString(
                dir.resolve(module + ".properties"),
                "module=" + module + "\nversion=" + version + "\n");
    }

    @Test
    void countsAddUpOverTheSuitesOfAModule() throws Exception {
        results(
                "core",
                "tests=\"10\" skipped=\"1\" failures=\"0\" errors=\"0\";tests=\"5\" skipped=\"0\" failures=\"1\" errors=\"2\"");

        Module core = VerificationRecord.read(root).get(0);

        assertEquals(15, core.tests());
        assertEquals(1, core.skipped());
        assertEquals(3, core.failed());
        assertFalse(core.verified());
    }

    @Test
    void aSkippedDialectIsNotVerifiedEvenThoughNothingFailed() throws Exception {
        results("oracle", "tests=\"40\" skipped=\"40\" failures=\"0\" errors=\"0\"");

        Module oracle = VerificationRecord.read(root).get(0);

        assertFalse(oracle.verified());
        assertTrue(oracle.status().contains("not verified"));
    }

    @Test
    void aDialectNeedsItsDatabaseToSayWhichOneItRanAgainst() throws Exception {
        results("postgresql", "tests=\"40\" skipped=\"0\" failures=\"0\" errors=\"0\"");
        assertFalse(VerificationRecord.read(root).get(0).verified());

        database("postgresql", "PostgreSQL 16.4");
        Module postgres = VerificationRecord.read(root).get(0);

        assertEquals("PostgreSQL 16.4", postgres.database());
        assertTrue(postgres.verified());
    }

    @Test
    void aModuleWithoutADatabaseIsVerifiedByItsTests() throws Exception {
        results("core", "tests=\"3\" skipped=\"0\" failures=\"0\" errors=\"0\"");

        assertTrue(VerificationRecord.read(root).get(0).verified());
    }

    @Test
    void theRecordNamesWhatIsNotVerified() throws Exception {
        results("core", "tests=\"3\" skipped=\"0\" failures=\"0\" errors=\"0\"");
        results("snowflake", "tests=\"9\" skipped=\"9\" failures=\"0\" errors=\"0\"");
        Info info = new Info("0.3.0", "2026-10-15", "abc123", "0.3.0", "c5d6e44", null, "21");

        String page = VerificationRecord.render(info, VerificationRecord.read(root));

        assertTrue(page.contains("# Verification of 0.3.0"));
        assertTrue(page.contains("| snowflake | 9 | 9 | 0 | not reached |"));
        assertTrue(page.contains("Not verified: snowflake."));
    }
}
