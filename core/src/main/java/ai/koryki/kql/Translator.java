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

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.iql.LinkResolver;
import ai.koryki.scaffold.domain.Attribute;
import ai.koryki.scaffold.domain.Entity;
import ai.koryki.scaffold.domain.Link;
import com.ibm.icu.impl.number.range.PrefixInfixSuffixLengthHelper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public interface Translator {

    default String source(String source) {
        return source;
    }

    default String field(String source, String field) {
        if (source == null) {
            throw new KorykiaiException();
        }
        return field;
    }

    default String crit(String crit) {
        return crit;
    }

//    static String translateToSchema(String query, LinkResolver resolver) {
//
//        KQLParser.QueryContext ctx;
//        String description;
//        try {
//            KQLReader reader = new KQLReader(query);
//            ctx = reader.getCtx();
//            description = reader.getDescription();
//        } catch (IOException e) {
//            throw new KorykiaiException(e);
//        }
//
//        return translateToSchema(ctx, description, resolver);
//    }
//
//    static String translateToSchema(KQLParser.QueryContext ctx, String description, LinkResolver resolver) {
//
//        DictionaryTranslator translator = resolver.dictionary();
//
//        KQLFormatter formatter2de = new KQLFormatter(ctx, description, resolver, translator);
//        return formatter2de.format();
//    }


}
