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

import ai.koryki.iql.rules.Aggregate;
import ai.koryki.databases.northwind.NorthwindService;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.Walker;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.validate.FunctionValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

public class ValidatorTest {

    private static LinkResolver resolver;

    @BeforeAll
    public static void readNorthwindDB() {

        resolver = NorthwindService.resolver();
    }

    @Test
    public void cycle1() throws IOException {

        InputStream kql = ValidatorTest.class.getResourceAsStream("/ai/koryki/kql/validator/cycle_1.kql");

        KQLReader r = new KQLReader(kql);

        KQLParser.QueryContext script = r.getCtx();

        KQLQueryMapper l = new KQLQueryMapper(resolver, script, r.getDescription());
        Query query = l.toBean();

        FunctionValidator v = new FunctionValidator(new Aggregate() {}, l.getIqlToContext());
        new Walker().walk(query, v);
        assertTrue(v.collect().isEmpty());
    }

    @Test
    public void expression1() throws IOException {

        InputStream kql = ValidatorTest.class.getResourceAsStream("/ai/koryki/kql/validator/invalid_expression_1.kql");

        KQLReader r = new KQLReader(kql);

        KQLParser.QueryContext script = r.getCtx();

        KQLQueryMapper l = new KQLQueryMapper(resolver, script, r.getDescription());
        Query query = l.toBean();

        FunctionValidator v = new FunctionValidator(new Aggregate() {}, l.getIqlToContext());
        new Walker().walk(query, v);
        assertFalse(v.collect().isEmpty());
    }

    @Test
    public void expression2() throws IOException {

        InputStream kql = ValidatorTest.class.getResourceAsStream("/ai/koryki/kql/validator/invalid_expression_2.kql");

        KQLReader r = new KQLReader(kql);

        KQLParser.QueryContext script = r.getCtx();

        KQLQueryMapper l = new KQLQueryMapper(resolver, script, r.getDescription());
        Query query = l.toBean();

        FunctionValidator v = new FunctionValidator(new Aggregate() {}, l.getIqlToContext());
        new Walker().walk(query, v);
        assertFalse(v.collect().isEmpty());
    }

    @Test
    public void function1() throws IOException {

        InputStream kql = ValidatorTest.class.getResourceAsStream("/ai/koryki/kql/validator/invalid_function_1.kql");

        KQLReader r = new KQLReader(kql);

        KQLParser.QueryContext script = r.getCtx();

        KQLQueryMapper l = new KQLQueryMapper(resolver, script, r.getDescription());
        Query query = l.toBean();

        FunctionValidator v = new FunctionValidator(new Aggregate() {}, l.getIqlToContext());
        new Walker().walk(query, v);
        assertFalse(v.collect().isEmpty());
    }

    @Test
    public void function2() throws IOException {

        InputStream kql = ValidatorTest.class.getResourceAsStream("/ai/koryki/kql/validator/function_2.kql");

        KQLReader r = new KQLReader(kql);

        KQLParser.QueryContext script = r.getCtx();

        KQLQueryMapper l = new KQLQueryMapper(resolver, script, r.getDescription());
        Query query = l.toBean();

        FunctionValidator v = new FunctionValidator(new Aggregate() {}, l.getIqlToContext());
        new Walker().walk(query, v);
        assertTrue(v.collect().isEmpty());
    }

    @Test
    public void function3() throws IOException {

        InputStream kql = ValidatorTest.class.getResourceAsStream("/ai/koryki/kql/validator/function_3.kql");

        KQLReader r = new KQLReader(kql);

        KQLParser.QueryContext script = r.getCtx();

        KQLQueryMapper l = new KQLQueryMapper(resolver, script, r.getDescription());
        Query query = l.toBean();

        FunctionValidator v = new FunctionValidator(new Aggregate() {}, l.getIqlToContext());
        new Walker().walk(query, v);
        assertTrue(v.collect().isEmpty());
    }

    @Test
    public void function4() throws IOException {

        InputStream kql = ValidatorTest.class.getResourceAsStream("/ai/koryki/kql/validator/function_4.kql");

        KQLReader r = new KQLReader(kql);

        KQLParser.QueryContext script = r.getCtx();

        KQLQueryMapper l = new KQLQueryMapper(resolver, script, r.getDescription());
        Query query = l.toBean();

        FunctionValidator v = new FunctionValidator(new Aggregate() {}, l.getIqlToContext());
        new Walker().walk(query, v);
        assertTrue(v.collect().isEmpty());
    }

    @Test
    public void function5() throws IOException {

        InputStream kql = ValidatorTest.class.getResourceAsStream("/ai/koryki/kql/validator/function_5.kql");

        KQLReader r = new KQLReader(kql);

        KQLParser.QueryContext script = r.getCtx();

        KQLQueryMapper l = new KQLQueryMapper(resolver, script, r.getDescription());
        Query query = l.toBean();

        FunctionValidator v = new FunctionValidator(new Aggregate() {}, l.getIqlToContext());
        new Walker().walk(query, v);
        assertTrue(v.collect().isEmpty());
    }

    @Test
    public void function6() throws IOException {

        InputStream kql = ValidatorTest.class.getResourceAsStream("/ai/koryki/kql/validator/function_6.kql");

        KQLReader r = new KQLReader(kql);

        KQLParser.QueryContext script = r.getCtx();

        KQLQueryMapper l = new KQLQueryMapper(resolver, script, r.getDescription());
        Query query = l.toBean();

        FunctionValidator v = new FunctionValidator(new Aggregate() {}, l.getIqlToContext());
        new Walker().walk(query, v);
        assertTrue(v.collect().isEmpty());
    }

    @Test
    public void function7() throws IOException {

        InputStream kql = ValidatorTest.class.getResourceAsStream("/ai/koryki/kql/validator/function_7.kql");

        KQLReader r = new KQLReader(kql);

        KQLParser.QueryContext script = r.getCtx();

        KQLQueryMapper l = new KQLQueryMapper(resolver, script, r.getDescription());
        Query query = l.toBean();

        FunctionValidator v = new FunctionValidator(new Aggregate() {}, l.getIqlToContext());
        new Walker().walk(query, v);
        assertTrue(v.collect().isEmpty());
    }

    @Test
    public void function8() throws IOException {

        InputStream kql = ValidatorTest.class.getResourceAsStream("/ai/koryki/kql/validator/function_8.kql");

        KQLReader r = new KQLReader(kql);

        KQLParser.QueryContext script = r.getCtx();

        KQLQueryMapper l = new KQLQueryMapper(resolver, script, r.getDescription());
        Query query = l.toBean();

        FunctionValidator v = new FunctionValidator(new Aggregate() {}, l.getIqlToContext());
        new Walker().walk(query, v);
        assertTrue(v.collect().isEmpty());
    }
}
