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

package ai.koryki.databases.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.WordingAudit;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.kql.KQLFormatter;
import ai.koryki.kql.KQLTranspiler;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A concept that columns share, end to end on DuckDB: {@code home}, {@code work} and the elements
 * of {@code route} are all the one concept {@code point}, defined once in the schema, and what the
 * model says of it, its wording and the names of its fields, is said once in its notion.
 *
 * <p>The German model calls the fields {@code breitengrad} and {@code laengengrad}, and a query
 * that writes those names reaches {@code lat} and {@code lon} in all three places, through a column
 * that refers to the concept and through a list whose element does. The other tests of selector
 * names run on columns with concepts of their own, which is the other way round.
 */
class SharedConceptTest {

    private static final String DB = "/ai/koryki/duckdb/databases/shared";
    private static final String ROOT = DB + "/model";

    private static Connection connection;

    @BeforeAll
    static void bed() throws SQLException {
        connection = DriverManager.getConnection("jdbc:duckdb:");
        try (Statement s = connection.createStatement()) {
            s.execute(
                    "CREATE TABLE check_shared (nr SMALLINT PRIMARY KEY,"
                            + " home STRUCT(lat DOUBLE, lon DOUBLE),"
                            + " work STRUCT(lat DOUBLE, lon DOUBLE),"
                            + " route STRUCT(lat DOUBLE, lon DOUBLE)[])");
            s.execute(
                    "INSERT INTO check_shared VALUES"
                            + " (1, {'lat': 53.5, 'lon': 10.0}, {'lat': 52.5, 'lon': 13.4},"
                            + "  [{'lat': 53.5, 'lon': 10.0}, {'lat': 52.5, 'lon': 13.4}]),"
                            + " (2, {'lat': 48.1, 'lon': 11.6}, NULL, [])");
        }
    }

    @AfterAll
    static void close() throws SQLException {
        connection.close();
    }

    private static LinkResolver resolver(String language) {
        Locale locale = Locale.forLanguageTag(language);
        return new LinkResolver(
                locale, CatalogLoader.db(DB), CatalogLoader.model(ROOT, locale), true);
    }

    /** The first column of every row, as text, for one query. */
    private static List<String> run(LinkResolver resolver, String kql) throws SQLException {
        String sql =
                KQLTranspiler.builder(kql, resolver)
                        .build()
                        .getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));
        List<String> rows = new ArrayList<>();
        try (Statement s = connection.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(String.valueOf(rs.getObject(1)));
            }
        }
        return rows;
    }

    @Test
    void theColumnsThatShareTheConceptAreTheStructItSpells() {
        LinkResolver resolver = resolver("en");

        for (String column : List.of("home", "work")) {
            var c =
                    resolver.getSchema()
                            .getTable("check_shared")
                            .orElseThrow()
                            .getColumn(column)
                            .orElseThrow();
            assertEquals("point", c.getConceptRef(), column);
            assertEquals("STRUCT(lat:FLOAT,lon:FLOAT)", resolver.getTypeDescriptor(c).portable());
        }
        var route =
                resolver.getSchema()
                        .getTable("check_shared")
                        .orElseThrow()
                        .getColumn("route")
                        .orElseThrow();
        assertEquals(
                "ARRAY(STRUCT(lat:FLOAT,lon:FLOAT))", resolver.getTypeDescriptor(route).portable());
    }

    @Test
    void theEnglishModelNamesTheFieldsAsTheSchemaDoes() throws SQLException {
        LinkResolver resolver = resolver("en");

        assertEquals(
                List.of("53.5", "48.1"), run(resolver, "FIND check_shared s FETCH s.home@lat"));
        assertEquals(
                List.of("13.4", "null"), run(resolver, "FIND check_shared s FETCH s.work@lon"));
    }

    @Test
    void theGermanNamesOfTheNotionReachTheFieldsOfEveryColumnThatSharesIt() throws SQLException {
        LinkResolver resolver = resolver("de");

        assertEquals(
                List.of("53.5", "48.1"), run(resolver, "FIND geteilt g FETCH g.heim@breitengrad"));
        assertEquals(
                List.of("13.4", "null"),
                run(resolver, "FIND geteilt g FETCH g.arbeit@laengengrad"));
        // through the element of a list whose element is the concept
        assertEquals(
                List.of("52.5", "null"),
                run(resolver, "FIND geteilt g FETCH g.strecke@2@breitengrad"));
    }

    @Test
    void aQueryMovesBetweenTheLanguagesThroughTheNotion() throws Exception {
        LinkResolver en = resolver("en");
        LinkResolver de = resolver("de");
        Schema schema = en.getSchema();
        KQLTranspiler transpiler =
                KQLTranspiler.builder("FIND check_shared s FETCH s.home@lat, s.route@1@lon", en)
                        .build();

        String german =
                new KQLFormatter(
                                transpiler.getCtx(),
                                transpiler.getDescription(),
                                en,
                                LinkResolver.dictionary(en.getModel(), de.getModel(), schema))
                        .format();

        assertTrue(german.contains("s.heim@breitengrad"), german);
        assertTrue(german.contains("s.strecke@1@laengengrad"), german);
        assertNotNull(KQLTranspiler.builder(german, de).build());
    }

    @Test
    void bothModelsSayInTheirOwnWordsWhatTheSchemaSays() {
        for (String language : List.of("en", "de")) {
            LinkResolver resolver = resolver(language);

            List<WordingAudit.Gap> missing =
                    WordingAudit.audit(resolver.getSchema(), resolver.getModel()).stream()
                            .filter(g -> g.kind() == WordingAudit.Kind.MISSING)
                            .toList();

            assertEquals(List.of(), missing, language);
        }
    }

    @Test
    void theGermanModelHasTranslatedTheNotion() {
        LinkResolver resolver = resolver("de");

        List<WordingAudit.Gap> unchanged =
                WordingAudit.audit(resolver.getSchema(), resolver.getModel()).stream()
                        .filter(g -> g.kind() == WordingAudit.Kind.UNCHANGED)
                        .filter(g -> g.where().startsWith("concept:point/"))
                        .toList();

        assertTrue(unchanged.isEmpty(), unchanged.toString());
    }
}
