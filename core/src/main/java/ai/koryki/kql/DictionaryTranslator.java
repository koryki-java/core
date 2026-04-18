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

import java.util.HashMap;
import java.util.Map;

public class DictionaryTranslator implements Translator {

    private Map<String, String> ld;
    private Map<String, TableDictionary> sd;

    public DictionaryTranslator(Map<String, String> ld, Map<String, TableDictionary> sd) {
        this.ld = ld;
        this.sd = sd;
    }

    public String source(String source) {
        TableDictionary dictionary = sd.get(source);
        if (dictionary == null) {
            // no exception, maybe it's a reference to blockid
            return source;
        }
        return dictionary.getName();
    }

    public String field(String source, String field) {
        TableDictionary dictionary = sd.get(source);
        if (dictionary == null) {
            // no exception, maybe it's a reference to blockid
            return field;
        }
        String c = dictionary.getColumns().get(field);
        return c != null ? c : field;
    }

    public String crit(String crit) {
        return ld.get(crit);
    }


}
