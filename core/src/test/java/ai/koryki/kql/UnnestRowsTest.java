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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.domain.Member;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.databases.complex.duckdb.ComplexService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.validate.Violation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The rows of a list made into rows: the element is typed from the list, its fields are the columns
 * of the alias, in the words of the model where it says them, and a column that is not there is
 * refused with the columns that are.
 */
class UnnestRowsTest {

    private static final LinkResolver BED = ComplexService.resolver();

    private static Map<String, Member> members(Object... keyAndMember) {
        Map<String, Member> map = new LinkedHashMap<>();
        for (int i = 0; i < keyAndMember.length; i += 2) {
            map.put((String) keyAndMember[i], (Member) keyAndMember[i + 1]);
        }
        return map;
    }

    /** The bed with a model that calls the fields of an item in German. */
    private static LinkResolver german() {
        LinkResolver bed = ComplexService.resolver();
        Schema schema = bed.getSchema();
        Model model = bed.getModel();
        Member sku = new Member("artikel");
        sku.setLabel("Artikelnummer");
        Member element = new Member("position");
        element.setMembers(members("sku", sku, "qty", new Member("menge")));
        model.getEntity("check_complex")
                .orElseThrow()
                .getAttribute("items")
                .orElseThrow()
                .setMembers(members("element", element));
        return new LinkResolver(Locale.GERMAN, schema, model);
    }

    private static KQLTranspiler on(LinkResolver resolver, String kql) {
        return KQLTranspiler.builder(kql, resolver)
                .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer())
                .build();
    }

    private static List<Violation> violations(LinkResolver resolver, String kql) {
        return on(resolver, kql).violations();
    }

    @Test
    void theFieldsOfAnElementAreTheColumnsOfTheAlias() {
        assertEquals(
                List.of(),
                violations(
                        BED,
                        "FIND check_complex t, t unnest(t.items) i"
                                + " FETCH t.nr, i.sku, i.qty, i.price, i.ordinal"));
    }

    @Test
    void anElementThatIsNoStructIsTheColumnValue() {
        assertEquals(
                List.of(),
                violations(
                        BED, "FIND check_complex t, t unnest(t.tags) g FETCH g.value, g.ordinal"));

        List<Violation> found =
                violations(BED, "FIND check_complex t, t unnest(t.tags) g FETCH g.sku");
        assertEquals(1, found.size(), found.toString());
        assertTrue(found.get(0).getMessage().contains("its columns are value, ordinal"));
    }

    @Test
    void aColumnThatIsNotThereIsRefusedWithTheOnesThatAre() {
        List<Violation> found =
                violations(BED, "FIND check_complex t, t unnest(t.items) i FETCH i.skuu");

        assertEquals(1, found.size(), found.toString());
        Violation v = found.get(0);
        assertEquals(Violation.UNKNOWN_COLUMN, v.getCategory());
        assertTrue(
                v.getMessage().contains("unknown column 'skuu' on the rows of 'i'"),
                v.getMessage());
        assertTrue(v.getMessage().contains("sku, qty, price"), v.getMessage());
        assertTrue(v.getDidYouMean().contains("sku"), v.getDidYouMean().toString());
    }

    @Test
    void theRowsAreTypedLikeTheFieldsOfTheElement() {
        List<ai.koryki.kql.HeaderInfo> infos =
                on(
                                BED,
                                "FIND check_complex t, t unnest(t.items) i"
                                        + " FETCH i.sku, i.qty, i.ordinal")
                        .infos(HeaderInfo::new);

        assertEquals(
                List.of("TEXT", "INTEGER", "INTEGER"),
                infos.stream().map(i -> i.getTypeDescriptor().getTypeFamily().name()).toList());
    }

    @Test
    void aFieldOfTheRowsMayBeUsedLikeAnyOther() {
        assertEquals(
                List.of(),
                violations(
                        BED,
                        "FIND check_complex t, t unnest(t.items) i"
                                + " FILTER i.qty > 1 FETCH i.sku, sum(i.qty * i.price) revenue"));
    }

    @Test
    void theListOfAnUnnestMayBeAFieldOfAnEarlierOne() {
        assertEquals(
                List.of(),
                violations(
                        BED,
                        "FIND check_complex t, t unnest(t.items) i, i unnest(i.opts) o"
                                + " FETCH t.nr, o.value"));
    }

    @Test
    void theListMayBeWhatALambdaMade() {
        assertEquals(
                List.of(),
                violations(
                        BED,
                        "FIND check_complex t, t unnest(list_filter(t.scores, x -> x > 1)) s"
                                + " FETCH s.value"));
    }

    @Test
    void aListThatHasNoTypeLeavesTheRowsOpenAndTheCallSaysWhy() {
        List<Violation> found =
                violations(BED, "FIND check_complex t, t unnest(t.nr) i FETCH i.value");

        assertEquals(1, found.size(), found.toString());
        assertTrue(found.get(0).getMessage().contains("unnest"), found.toString());
    }

    @Test
    void theModelsWordsNameTheFieldsAndAreTranslatedToTheKeys() {
        LinkResolver de = german();
        KQLTranspiler transpiler =
                on(de, "FIND check_complex t, t unnest(t.items) i FETCH i.artikel, i.menge");

        assertEquals(List.of(), transpiler.violations());
        List<String> fetched =
                transpiler.getOut().stream()
                        .map(o -> o.getExpression().getField().getName())
                        .toList();
        assertEquals(List.of("sku", "qty"), fetched, "after the rules the fields have their keys");
    }

    @Test
    void theKeysStayValidUnderAModelThatRenamesThem() {
        assertEquals(
                List.of(),
                violations(
                        german(),
                        "FIND check_complex t, t unnest(t.items) i FETCH i.sku, i.price"));
    }

    @Test
    void aWrongWordIsRefusedInTheWordsOfTheModel() {
        List<Violation> found =
                violations(german(), "FIND check_complex t, t unnest(t.items) i FETCH i.artikell");

        assertEquals(1, found.size(), found.toString());
        assertTrue(found.get(0).getMessage().contains("artikel, menge, price"), found.toString());
        assertTrue(found.get(0).getDidYouMean().contains("artikel"), found.toString());
    }

    @Test
    void theHeaderIsTheLabelOfTheMember() {
        List<ai.koryki.kql.HeaderInfo> infos =
                on(german(), "FIND check_complex t, t unnest(t.items) i FETCH i.artikel, i.menge")
                        .infos(HeaderInfo::new);

        assertEquals(
                List.of("Artikelnummer", "menge"), infos.stream().map(i -> i.getHeader()).toList());
    }

    @Test
    void anAliasIsNoEntityToCount() {
        List<Violation> found =
                violations(BED, "FIND check_complex t, t unnest(t.items) i FETCH count(i)");

        assertEquals(1, found.size(), found.toString());
        assertEquals("identity", found.get(0).getCategory());
        assertTrue(
                found.get(0).getMessage().contains("is a list made into rows and has no key"),
                found.get(0).getMessage());
        assertTrue(found.get(0).getMessage().contains("count(i.sku)"), found.get(0).getMessage());
    }
}
