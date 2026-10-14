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
package ai.koryki.tools.migrate;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.ConceptConverter;
import ai.koryki.catalog.Util;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.catalog.types.ConceptResolver;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeDescriptorParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Rewrites a {@code db.json} so that every column says its type and its wording in a concept of its
 * own, and not in fields of the column: {@code MigrateCatalogs db.json ...}. A one-off tool; it
 * goes with the old fields.
 *
 * <p>Nothing is written that is not the same: before the file is replaced, every column of the new
 * catalog is checked to have the type, the label, the comment, the description, the unit, the
 * quantity, the presentation and the encoding of the old one, and the new text is read back and
 * checked again.
 */
public final class MigrateCatalogs {

    private MigrateCatalogs() {}

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    /** What was done to a file. */
    public record Report(Path file, int columns, int migrated, List<String> kept) {}

    public static void main(String... files) throws IOException {
        for (String file : files) {
            Report report = migrate(Path.of(file));
            System.out.println(
                    report.file()
                            + ": "
                            + report.migrated()
                            + " of "
                            + report.columns()
                            + " columns have a concept"
                            + (report.kept().isEmpty()
                                    ? ""
                                    : ", kept as they are: " + report.kept()));
        }
    }

    /**
     * Rewrites the file.
     *
     * @throws IllegalStateException when the new catalog would not say what the old one said
     */
    public static Report migrate(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Schema old = CatalogLoader.readSchemaJson(text);
        Schema migrated = ConceptConverter.migrate(old);
        verify(file, old, migrated);

        Util.write(migrated, file.toFile());
        Schema again = CatalogLoader.readSchemaJson(Files.readString(file, StandardCharsets.UTF_8));
        verify(file, old, again);

        int columns = 0;
        int done = 0;
        List<String> kept = new ArrayList<>();
        for (Table table : again.getTables()) {
            for (Column column : table.getColumns()) {
                columns++;
                if (column.getConcept() != null || column.getConceptRef() != null) {
                    done++;
                } else {
                    kept.add(table.getName() + "." + column.getName());
                }
            }
        }
        return new Report(file, columns, done, kept);
    }

    private static void verify(Path file, Schema old, Schema now) {
        ConceptResolver resolver = new ConceptResolver(now);
        ConceptResolver wasResolver = new ConceptResolver(old);
        for (int t = 0; t < old.getTables().size(); t++) {
            Table before = old.getTables().get(t);
            Table after = now.getTables().get(t);
            if (before.getColumns().size() != after.getColumns().size()) {
                throw new IllegalStateException(file + ": the columns of " + before.getName());
            }
            for (int c = 0; c < before.getColumns().size(); c++) {
                Column was = before.getColumns().get(c);
                Column is = after.getColumns().get(c);
                String where = file + " " + before.getName() + "." + was.getName();
                if (!Objects.equals(was.getName(), is.getName())
                        || was.getPkPos() != is.getPkPos()
                        || was.getNamePos() != is.getNamePos()
                        || !Objects.equals(was.getNullable(), is.getNullable())) {
                    throw new IllegalStateException(where + ": what belongs to the table changed");
                }
                if (is.getConcept() == null) {
                    continue;
                }
                Concept concept = is.getConcept();
                // what the old column said: its own concept, or the concept its fields describe
                Concept said = ConceptConverter.of(was);
                if (said == null) {
                    throw new IllegalStateException(where + ": it said nothing a concept can keep");
                }
                same(where, "label", said.getLabel(), concept.getLabel());
                same(where, "comment", said.getComment(), concept.getComment());
                same(where, "description", said.getDescription(), concept.getDescription());
                same(where, "unit", said.getUnit(), concept.getUnit());
                same(where, "quantity", said.getQuantity(), concept.getQuantity());
                same(where, "presentation", said.getPresentation(), concept.getPresentation());
                same(where, "encoding", said.getTypeEncoding(), concept.getTypeEncoding());
                if (was.getTypeFamily() != null) {
                    // the type as the database spells it is kept
                    same(where, "dialectType", was.getDialectType(), concept.getDialectType());
                }
                if (said.getTypeFamily() == null) {
                    continue; // wording alone: there is no type to compare
                }
                TypeDescriptor typed =
                        was.getConcept() != null
                                ? wasResolver.descriptor(was, PARSER)
                                : PARSER.parse(was);
                if (!typed.equals(resolver.descriptor(is, PARSER))) {
                    throw new IllegalStateException(where + ": the type is not the same");
                }
            }
        }
    }

    private static void same(String where, String what, String was, String is) {
        if (!Objects.equals(was, is)) {
            throw new IllegalStateException(where + ": the " + what + " is not the same");
        }
    }
}
