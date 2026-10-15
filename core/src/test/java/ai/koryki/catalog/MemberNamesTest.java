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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.domain.Attribute;
import ai.koryki.catalog.domain.Entity;
import ai.koryki.catalog.domain.Member;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.domain.Notion;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Entry;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.databases.complex.duckdb.ComplexService;
import ai.koryki.iql.LinkResolver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The names of the parts of a nested column in the language of a model, on the complex bed: the
 * struct {@code address} has the fields street, geo (lat, lon) and phones, and the model of this
 * test is a German one.
 */
class MemberNamesTest {

    private static Schema schema() {
        return CatalogLoader.db(ComplexService.DB);
    }

    private static Member member(String name) {
        return new Member(name);
    }

    private static Map<String, Member> members(Object... keyAndMember) {
        Map<String, Member> map = new LinkedHashMap<>();
        for (int i = 0; i < keyAndMember.length; i += 2) {
            map.put((String) keyAndMember[i], (Member) keyAndMember[i + 1]);
        }
        return map;
    }

    /** The draft model of the bed, and the attribute of the column, ready to be given members. */
    private static Model model(Schema schema) {
        return SchemaToModel.convert(schema);
    }

    private static Entity entity(Model model) {
        return model.getEntity("check_complex").orElseThrow();
    }

    private static Attribute attribute(Model model, String name) {
        return entity(model).getAttribute(name).orElseThrow();
    }

    /** address, with the fields of the German model. */
    private static Model german(Schema schema) {
        Model model = model(schema);
        Member geo = member("position");
        geo.setMembers(members("lat", member("breitengrad"), "lon", member("laengengrad")));
        attribute(model, "address").setMembers(members("street", member("strasse"), "geo", geo));
        return model;
    }

    private static MemberNames.Level address(Schema schema, Model model) {
        MemberNames names = new MemberNames(schema, model);
        return names.of(entity(model), attribute(model, "address")).orElseThrow();
    }

    @Test
    void aNameOfTheLanguageIsTheEntryOfTheSchema() {
        Schema schema = schema();
        MemberNames.Level level = address(schema, german(schema));

        assertEquals("street", level.physical("strasse").orElseThrow());
        assertEquals("geo", level.physical("position").orElseThrow());
        assertEquals("geo", level.physical("POSITION").orElseThrow());
        // a field the model says nothing about keeps its physical name
        assertEquals("phones", level.physical("phones").orElseThrow());
        assertTrue(level.physical("nothing").isEmpty());
        // the physical name of a renamed field is not a name of the language
        assertTrue(level.physical("street").isEmpty());
    }

    @Test
    void anEntryIsCalledWhatTheLanguageCallsIt() {
        Schema schema = schema();
        MemberNames.Level level = address(schema, german(schema));

        assertEquals(List.of("street", "geo", "phones"), level.keys());
        assertEquals(List.of("strasse", "position", "phones"), level.names());
        assertEquals("position", level.written("geo"));
    }

    @Test
    void theNamesGoOnBelowAnEntryThatIsAConceptOfItsOwn() {
        Schema schema = schema();
        MemberNames.Level geo = address(schema, german(schema)).into("geo").orElseThrow();

        assertEquals(List.of("breitengrad", "laengengrad"), geo.names());
        assertEquals("lat", geo.physical("Breitengrad").orElseThrow());
    }

    @Test
    void aPartThatIsOnlyATypeHasNothingBelowIt() {
        Schema schema = schema();

        Model model = model(schema);
        MemberNames names = new MemberNames(schema, model);
        MemberNames.Level matrix =
                names.of(entity(model), attribute(model, "matrix")).orElseThrow();
        // the rows of the matrix are a concept of their own, the numbers in them only a type
        MemberNames.Level row = matrix.into("element").orElseThrow();

        assertTrue(row.into("element").isEmpty());
        assertTrue(row.into("nothing").isEmpty());
        // a field that is a concept of its own, however plain, is a level with nothing to name
        assertEquals(
                List.of(), address(schema, german(schema)).into("street").orElseThrow().keys());
    }

    @Test
    void theFieldsOfTheStructsInAListAreNamedUnderElement() {
        Schema schema = schema();
        Model model = model(schema);
        Member element = new Member();
        element.setMembers(members("sku", member("artikel"), "qty", member("menge")));
        attribute(model, "items").setMembers(members("element", element));

        MemberNames names = new MemberNames(schema, model);
        MemberNames.Level items = names.of(entity(model), attribute(model, "items")).orElseThrow();
        MemberNames.Level row = items.into("element").orElseThrow();

        assertEquals("sku", row.physical("artikel").orElseThrow());
        assertEquals("qty", row.physical("menge").orElseThrow());
        assertEquals("price", row.physical("price").orElseThrow());
        // a list has no field to write by name
        assertEquals(List.of(), items.keys());
    }

    // ---- a concept that columns refer to by name ---------------------------------------------

    /** The schema with address as a concept of the schema and the column referring to it. */
    private static Schema shared() {
        Schema schema = schema();
        Concept concept =
                schema.getTable("check_complex")
                        .orElseThrow()
                        .getColumn("address")
                        .orElseThrow()
                        .getConcept();
        schema.setConcepts(new LinkedHashMap<>(Map.of("address_type", concept)));
        var column =
                schema.getTable("check_complex").orElseThrow().getColumn("address").orElseThrow();
        column.setConcept(null);
        column.setConceptRef("address_type");
        return schema;
    }

    @Test
    void aNotionSaysTheNamesOnceForEveryColumnThatReferstoTheConcept() {
        Schema schema = shared();
        Model model = model(schema);
        Notion notion = new Notion();
        notion.setMembers(members("street", member("strasse")));
        model.getNotions().put("address_type", notion);

        MemberNames names = new MemberNames(schema, model);
        MemberNames.Level level =
                names.of(entity(model), attribute(model, "address")).orElseThrow();

        assertEquals("street", level.physical("strasse").orElseThrow());
    }

    @Test
    void whatTheAttributeSaysWinsOverTheNotion() {
        Schema schema = shared();
        Model model = model(schema);
        Notion notion = new Notion();
        notion.setMembers(members("street", member("strasse")));
        model.getNotions().put("address_type", notion);
        attribute(model, "address").setMembers(members("street", member("gasse")));

        MemberNames names = new MemberNames(schema, model);
        MemberNames.Level level =
                names.of(entity(model), attribute(model, "address")).orElseThrow();

        assertEquals("street", level.physical("gasse").orElseThrow());
        assertTrue(level.physical("strasse").isEmpty());
    }

    @Test
    void anEntryThatNamesAConceptTakesItsNamesFromTheNotionOfThatConcept() {
        Schema schema = schema();
        Concept position =
                schema.getTable("check_complex")
                        .orElseThrow()
                        .getColumn("address")
                        .orElseThrow()
                        .getConcept()
                        .getEntries()
                        .get("geo")
                        .getConcept();
        schema.setConcepts(new LinkedHashMap<>(Map.of("position", position)));
        schema.getTable("check_complex")
                .orElseThrow()
                .getColumn("address")
                .orElseThrow()
                .getConcept()
                .getEntries()
                .put("geo", Entry.of("position"));
        Model model = model(schema);
        Notion notion = new Notion();
        notion.setMembers(members("lat", member("breitengrad")));
        model.getNotions().put("position", notion);

        MemberNames.Level geo = address(schema, model).into("geo").orElseThrow();

        assertEquals("lat", geo.physical("breitengrad").orElseThrow());
    }

    // ---- what does not fit ---------------------------------------------------------------------

    private static String refused(Schema schema, Model model) {
        return assertThrows(
                        IllegalArgumentException.class,
                        () -> new MemberNames(schema, model).check())
                .getMessage();
    }

    @Test
    void aModelThatFitsTheSchemaPasses() {
        Schema schema = schema();

        new MemberNames(schema, german(schema)).check();
        new MemberNames(schema, model(schema)).check();
    }

    @Test
    void aMemberHasToBeAnEntryOfItsConcept() {
        Schema schema = schema();
        Model model = model(schema);
        attribute(model, "address").setMembers(members("zip", member("plz")));

        assertEquals(
                "attribute 'check_complex.address', member 'zip' is no entry of its concept; it has"
                        + " street, geo, phones",
                refused(schema, model));
    }

    @Test
    void twoFieldsMayNotHaveTheSameNameInTheLanguage() {
        Schema schema = schema();
        Model model = model(schema);
        attribute(model, "address").setMembers(members("street", member("phones")));

        assertEquals(
                "attribute 'check_complex.address': two fields are called 'phones' in this language",
                refused(schema, model));
    }

    @Test
    void theNamesOfTheFieldsAreComparedWithoutRegardToCase() {
        Schema schema = schema();
        Model model = model(schema);
        attribute(model, "address").setMembers(members("street", member("GEO")));

        assertEquals(
                "attribute 'check_complex.address': two fields are called 'geo' in this language",
                refused(schema, model));
    }

    @Test
    void aMemberThatIsOnlyATypeCannotHaveMembers() {
        Schema schema = schema();
        Model model = model(schema);
        Member number = new Member();
        number.setMembers(members("x", member("y")));
        Member row = new Member();
        row.setMembers(members("element", number));
        attribute(model, "matrix").setMembers(members("element", row));

        assertEquals(
                "attribute 'check_complex.matrix', member 'element', member 'element' names members,"
                        + " but its entry is a type and has no parts",
                refused(schema, model));
    }

    @Test
    void aNameMayNotBeBlank() {
        Schema schema = schema();
        Model model = model(schema);
        attribute(model, "address").setMembers(members("street", member(" ")));

        assertTrue(refused(schema, model).endsWith("member 'street' has a blank name"));
    }

    @Test
    void aNotionIsTheNotionOfAConceptTheSchemaHas() {
        Schema schema = schema();
        Model model = model(schema);
        model.getNotions().put("nowhere", new Notion());

        assertEquals(
                "notion 'nowhere': the schema has no concept of that name; it has none",
                refused(schema, model));
    }

    @Test
    void aColumnWithoutAConceptHasNoPartsToName() {
        Schema schema = schema();
        schema.getTable("check_complex")
                .orElseThrow()
                .getColumn("address")
                .orElseThrow()
                .setConcept(null);
        Model model = model(schema);
        attribute(model, "address").setMembers(members("street", member("strasse")));

        assertEquals(
                "attribute 'check_complex.address': it names members, but its column has no concept"
                        + " to name them in",
                refused(schema, model));
    }

    @Test
    void theResolverOfAModelRefusesWhatDoesNotFit() {
        Schema schema = schema();
        Model model = model(schema);
        attribute(model, "address").setMembers(members("zip", member("plz")));

        assertThrows(
                IllegalArgumentException.class,
                () -> new LinkResolver(Locale.ENGLISH, schema, model, true));
        new LinkResolver(Locale.ENGLISH, schema, german(schema), true);
    }

    // ---- the model as a file and as a copy
    // -------------------------------------------------------

    @Test
    void theMembersAreReadFromModelJson() {
        Model model =
                CatalogLoader.readModelJson(
                        new java.io.ByteArrayInputStream(
                                """
                                { "name": "m", "entities": [ { "name": "check_complex", "attributes": [
                                    { "name": "address", "members": {
                                        "street": { "name": "strasse", "label": "Strasse" },
                                        "geo": { "name": "position", "members": {
                                            "lat": { "name": "breitengrad" } } } } } ] } ],
                                  "links": [],
                                  "notions": { "address_type": { "label": "Anschrift", "members": {
                                      "street": { "name": "strasse" } } } } }
                                """
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        Attribute address = attribute(model, "address");
        assertEquals("strasse", address.getMembers().get("street").getName());
        assertEquals(
                "breitengrad", address.getMembers().get("geo").getMembers().get("lat").getName());
        assertEquals(
                "strasse",
                model.getNotions().get("address_type").getMembers().get("street").getName());
    }

    @Test
    void aCopyOfTheModelHasItsOwnCopyOfTheMembers() {
        Schema schema = schema();
        Model model = german(schema);

        Model copy = Model.deepCopy(model);
        Member geo = attribute(copy, "address").getMembers().get("geo");
        geo.setName("changed");
        geo.getMembers().get("lat").setName("changed");

        assertNotSame(
                attribute(model, "address").getMembers(), attribute(copy, "address").getMembers());
        assertEquals("position", attribute(model, "address").getMembers().get("geo").getName());
        assertEquals(
                "breitengrad",
                attribute(model, "address")
                        .getMembers()
                        .get("geo")
                        .getMembers()
                        .get("lat")
                        .getName());
        assertEquals(
                attribute(model, "address").getColumn(), attribute(copy, "address").getColumn());
        assertFalse(attribute(copy, "address").getMembers().isEmpty());
    }
}
