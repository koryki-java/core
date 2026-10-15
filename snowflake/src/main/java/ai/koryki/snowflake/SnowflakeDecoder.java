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
package ai.koryki.snowflake;

import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.jdbc.CoreDecoder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the nested values Snowflake's JDBC driver hands back as JSON text.
 *
 * <p>An ARRAY, an OBJECT (the struct), a MAP and a VARIANT (the JSON) all arrive as one string,
 * pretty-printed, whatever the type inside. The text is parsed and walked along the column's type
 * tree: a struct reads its fields in the order the type declares, found by name without regard to
 * case, a map's keys become integers where the type says so, and every leaf becomes the Java value
 * of its family, so dates, timestamps and decimals inside a container are what they would be in a
 * column of their own. The result is handed to {@link CoreDecoder}, which does the rest.
 *
 * <p>A JSON value is only compacted, so it prints the same as on the other dialects.
 */
public class SnowflakeDecoder extends CoreDecoder {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public SnowflakeDecoder(ZoneId zone) {
        super(zone);
    }

    @Override
    public Object decode(Object v, ColumnInfo info) {
        TypeDescriptor td = info != null ? info.getTypeDescriptor() : null;
        if (td != null && v instanceof String text) {
            if (td.isComplex()) {
                return super.decode(read(parse(text), td), info);
            } else if (CoreTypeFamily.JSON.equals(td.getTypeFamily()) && !text.isBlank()) {
                return parse(text).toString();
            }
        }
        return super.decode(v, info);
    }

    private static JsonNode parse(String text) {
        return MAPPER.readTree(nulls(text));
    }

    /**
     * The driver writes a NULL element of an array as the bare word {@code undefined}, which is no
     * JSON. Replaced by {@code null} outside of string literals, where the word is just text.
     */
    static String nulls(String text) {
        if (!text.contains("undefined")) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        boolean inString = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\\' && i + 1 < text.length()) {
                    out.append(text.charAt(++i));
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
                out.append(c);
            } else if (text.startsWith("undefined", i)) {
                out.append("null");
                i += "undefined".length() - 1;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** The Java value of one node, by the type it has in the tree. */
    static Object read(JsonNode node, TypeDescriptor td) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        var family = td.getTypeFamily();
        if (CoreTypeFamily.ARRAY.equals(family)) {
            List<Object> out = new ArrayList<>();
            for (JsonNode e : node) {
                out.add(read(e, td.element()));
            }
            return out;
        } else if (CoreTypeFamily.STRUCT.equals(family)) {
            Object[] out = new Object[td.fieldNames().size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = read(field(node, td.fieldNames().get(i)), td.children().get(i));
            }
            return out;
        } else if (CoreTypeFamily.MAP.equals(family)) {
            Map<Object, Object> out = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> e : node.properties()) {
                out.put(key(e.getKey(), td.key()), read(e.getValue(), td.value()));
            }
            return out;
        } else if (CoreTypeFamily.JSON.equals(family)) {
            return node.toString();
        } else if (CoreTypeFamily.INTEGER.equals(family)) {
            return node.isString() ? Long.valueOf(node.stringValue().trim()) : node.longValue();
        } else if (CoreTypeFamily.DECIMAL.equals(family)) {
            return node.isString()
                    ? new BigDecimal(node.stringValue().trim())
                    : node.decimalValue();
        } else if (CoreTypeFamily.FLOAT.equals(family)) {
            return node.isString() ? Double.valueOf(node.stringValue().trim()) : node.doubleValue();
        } else if (CoreTypeFamily.BOOLEAN.equals(family)) {
            return node.isString() ? Boolean.valueOf(node.stringValue()) : node.booleanValue();
        } else if (CoreTypeFamily.DATE.equals(family) && node.isString()) {
            return LocalDate.parse(node.stringValue().trim().substring(0, 10));
        } else if (CoreTypeFamily.TIMESTAMP.equals(family) && node.isString()) {
            return LocalDateTime.parse(node.stringValue().trim().replace(' ', 'T'));
        } else if (CoreTypeFamily.TIME.equals(family) && node.isString()) {
            return LocalTime.parse(node.stringValue().trim());
        }
        return node.isString() ? node.stringValue() : node.toString();
    }

    private static JsonNode field(JsonNode object, String name) {
        JsonNode exact = object.get(name);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, JsonNode> e : object.properties()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** JSON has text keys only; the type says when they were integers. */
    private static Object key(String key, TypeDescriptor td) {
        return CoreTypeFamily.INTEGER.equals(td.getTypeFamily()) ? Long.valueOf(key.trim()) : key;
    }
}
