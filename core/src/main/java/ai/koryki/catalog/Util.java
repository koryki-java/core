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
package ai.koryki.catalog;

import ai.koryki.antlr.KorykiaiException;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes text and JSON output to files (golden generation and exports). Catalog loading lives
 * in {@link CatalogLoader}.
 */
public class Util {

    /**
     * The line break in everything koryki produces as text — a fixed {@code \n}, not the platform's.
     *
     * <p>Generated SQL, IQL, KQL and CSV are data formats, not console text: they go to drivers and
     * into golden files. With {@code System.lineSeparator()} the same query would yield different
     * bytes depending on the operating system, and every golden would depend on the machine that
     * produced it. {@code FileAsserter} splits on {@code \n} when comparing anyway — generation
     * follows suit here.
     *
     * <p>Lives in {@code catalog} because {@code iql} and {@code jdbc} both point here; the other
     * way round would be a package cycle ({@code iql.typing.IntervalEncodings} already imports from
     * {@code jdbc}).
     */
    public static final String NL = ai.koryki.antlr.Text.NL;

    public static void text(Object text, File out) {
        text(text, out, StandardCharsets.UTF_8);
    }

    /**
     * Writes through a temporary file in the target directory and then renames it.
     *
     * <p>The direct route ({@code new FileOutputStream(out)}) truncates the target to zero and
     * fills it afterwards — in between lies a state in which it is incomplete. That is no
     * theoretical gap here: the shared goldens under {@code expected/kql/…} are <em>one</em> path
     * for all dialects, and the eight dialect modules run as separate test processes in parallel.
     * When a golden is missing, all eight write it at once. On 2026-08-05 two of the 242 new type
     * goldens came out mutilated that way ({@code waravg_price} instead of {@code avg_price},
     * {@code wId} instead of {@code Id}) and were committed like that — fragments of one write in
     * front of the content of the other.
     *
     * <p>With a temporary file and {@code ATOMIC_MOVE} every writer publishes a complete file;
     * whoever renames last wins. Since shared goldens carry the same content that is harmless — and
     * a concurrent reader never sees half a file. The temporary file must lie in the <em>same</em>
     * directory, or the rename may cross a filesystem boundary and stops being atomic.
     *
     * <p>An {@code OutputStreamWriter} writes instead of a {@code PrintWriter}: the latter records
     * IO errors only in a flag nobody queries, and would have left an aborted write just as
     * silently. {@code String.valueOf} keeps the previous behaviour for {@code null}.
     */
    public static void text(Object text, File out, Charset cs) {

        if (out.getParentFile() != null && !out.getParentFile().exists()) {
            out.getParentFile().mkdirs();
        }
        Path target = out.toPath();
        Path dir = target.getParent() != null ? target.getParent() : Path.of(".");
        Path tmp = null;
        try {
            tmp = Files.createTempFile(dir, out.getName(), ".tmp");
            try (Writer w = new OutputStreamWriter(Files.newOutputStream(tmp), cs)) {
                w.write(String.valueOf(text));
            }
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp = null;
        } catch (IOException e) {
            throw new KorykiaiException(e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // The real failure is already on its way; a leftover in the target directory
                    // must not obscure it.
                }
            }
        }
    }

    public static void write(Object value, File out) {
        write(value, out, StandardCharsets.UTF_8);
    }

    public static void write(Object value, File out, Charset cs) {
        // Jackson 3 throws unchecked JacksonException; text() wraps its own IO errors
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);

        text(json, out, cs);
    }
}
