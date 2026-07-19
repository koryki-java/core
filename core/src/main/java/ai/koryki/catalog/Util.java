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
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Writes text and JSON output to files (golden generation and exports). Catalog loading lives
 * in {@link CatalogLoader}.
 */
public class Util {

    public static void text(Object text, File out) {
        text(text, out, StandardCharsets.UTF_8);
    }

    public static void text(Object text, File out, Charset cs) {

        if (out.getParentFile() != null && !out.getParentFile().exists()) {
            out.getParentFile().mkdirs();
        }
        try {
            try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out), cs))) {
                w.print(text);
            }
        } catch (IOException e) {
            throw new KorykiaiException(e);
        }
    }

    public static void write(Object value, File out) {
        write(value, out, StandardCharsets.UTF_8);
    }

    public static void write(Object value, File out, Charset cs) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);

            text(json, out, cs);
        } catch (IOException e) {
            throw new KorykiaiException(e);
        }
    }
}
