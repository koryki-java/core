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

package ai.koryki.databases.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.koryki.catalog.WordingAudit;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.Engine;
import ai.koryki.kql.HeaderInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * A concept that columns share, on one engine: the bed {@code check_shared}, in which {@code home},
 * {@code work} and the elements of {@code route} are all the one concept {@code point}, defined
 * once in the schema and worded once in the notion of the model. The German model calls its fields
 * {@code breitengrad} and {@code laengengrad}.
 *
 * <p>The same rows and the same answers on every engine, so a dialect that renders a field of a
 * shared concept differently from the others shows here. A dialect test extends this and says how
 * to reach its database; the bed is created by the script {@code data_check_shared.sql} of its
 * Northwind module.
 */
public abstract class SharedConceptContract {

    /** The engine of this dialect over the catalog of the bed, in the language asked for. */
    protected abstract Engine<HeaderInfo, ListWithSqlResult<HeaderInfo>> engine(Locale locale);

    /** The resolver of the bed in the language asked for, for the wording check. */
    protected abstract LinkResolver resolver(Locale locale);

    /** The first column of the answer, in the order of the rows. */
    private List<Double> first(Locale locale, String kql) {
        List<Double> values = new ArrayList<>();
        for (List<Object> row : engine(locale).executeKQL(kql, ListWithSqlResult::new).getRows()) {
            Object value = row.get(0);
            values.add(value == null ? null : Double.valueOf(String.valueOf(value)));
        }
        return values;
    }

    @Test
    public void theEnglishModelNamesTheFieldsAsTheSchemaDoes() {
        assertEquals(
                java.util.Arrays.asList(53.5, 48.1),
                first(Locale.ENGLISH, "FIND check_shared s FETCH s.home@lat, s.nr ASC"));
        assertEquals(
                java.util.Arrays.asList(13.4, null),
                first(Locale.ENGLISH, "FIND check_shared s FETCH s.work@lon, s.nr ASC"));
    }

    @Test
    public void theGermanNamesOfTheNotionReachTheFieldsOfEveryColumnThatSharesIt() {
        Locale de = Locale.GERMAN;

        assertEquals(
                java.util.Arrays.asList(53.5, 48.1),
                first(de, "FIND geteilt g FETCH g.heim@breitengrad, g.nr ASC"));
        assertEquals(
                java.util.Arrays.asList(13.4, null),
                first(de, "FIND geteilt g FETCH g.arbeit@laengengrad, g.nr ASC"));
        // through the element of a list whose element is the concept
        assertEquals(
                java.util.Arrays.asList(52.5, null),
                first(de, "FIND geteilt g FETCH g.strecke@2@breitengrad, g.nr ASC"));
    }

    @Test
    public void bothModelsSayInTheirOwnWordsWhatTheSchemaSays() {
        for (Locale locale : List.of(Locale.ENGLISH, Locale.GERMAN)) {
            LinkResolver resolver = resolver(locale);

            List<WordingAudit.Gap> missing =
                    WordingAudit.audit(resolver.getSchema(), resolver.getModel()).stream()
                            .filter(g -> g.kind() == WordingAudit.Kind.MISSING)
                            .toList();

            assertEquals(List.of(), missing, locale.toString());
        }
    }
}
