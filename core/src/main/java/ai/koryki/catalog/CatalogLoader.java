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
import ai.koryki.catalog.domain.Link;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads catalog objects from JSON resources on the classpath: the physical {@link Schema}
 * ({@code db.json}), the semantic {@link Model} ({@code model.json}, locale-scoped), the FK
 * blacklist and link-info side files. The write/output side lives in {@link Util}.
 */
public class CatalogLoader {

    public static final String DB_RESOURCE = "/db.json";
    public static final String MODEL_RESOURCE = "/model.json";
    @Deprecated
    public static final String SCHEMA_RESOURCE = "/schema.json";
    public static final String BLACKLIST_RESOURCE = "/blacklist.json";

    public static Schema db(String resourceRoot) {
        InputStream i = CatalogLoader.class.getResourceAsStream(resource(resourceRoot, DB_RESOURCE));
        return readSchemaJson(i);
    }

    public static Model model(String resourceRoot, Locale locale) {
        InputStream i = CatalogLoader.class.getResourceAsStream(modelResource(resourceRoot, locale));
        Model m = readModelJson(i);
        return m;
    }

    public static String modelResource(String resourceRoot, Locale locale) {
        String r = localResource(resourceRoot, locale, MODEL_RESOURCE);
        return r;
    }

    public static Map<String, List<String>> blacklist(String root) {
        String r = root;
        if (r.endsWith("/")) {
            r = r.substring(1);
        }
        return readHashSetFromResource(r + BLACKLIST_RESOURCE);
    }

    public static String localResource(String root, Locale locale, String resource) {
        String r = root;
        if (!r.endsWith("/")) {
            r = r + "/";
        }
        String s = resource;
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        return r + locale.getLanguage() + "/" + s;
    }

    public static String resource(String root, String resource) {
        String r = root;
        if (!r.endsWith("/")) {
            r = r + "/";
        }
        String s = resource;
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        return r + s;
    }

    public static List<Link> readInfos(InputStream in) {

        try {
            ObjectMapper mapper = new ObjectMapper();
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return mapper.readValue(r, new TypeReference<List<Link>>() {});
            }
        } catch (IOException e) {
            throw new KorykiaiException(e);
        }
    }

    public static List<Link> readInfos(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, new TypeReference<List<Link>>() {});
        } catch (IOException e) {
            throw new KorykiaiException(e);
        }
    }

    public static Schema readSchemaJson(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, Schema.class);
        } catch (IOException e) {
            throw new KorykiaiException(e);
        }
    }

    public static Model readModelJson(InputStream in) {

        try {
            ObjectMapper mapper = new ObjectMapper();
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                Model model = mapper.readValue(r, Model.class);
                return model;
            }
        } catch (IOException e) {
            throw new KorykiaiException(e);
        }
    }

    public static Schema readSchemaJson(InputStream in) {

        try {
            ObjectMapper mapper = new ObjectMapper();
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return mapper.readValue(r, Schema.class);
            }
        } catch (IOException e) {
            throw new KorykiaiException(e);
        }
    }

    public static <K, V> Map<K, V> readHashSetFromStream(InputStream in, TypeReference<Map<K, V>> ref) {

        try {
            ObjectMapper mapper = new ObjectMapper();
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {

                return mapper.readValue(r, ref);
            }
        } catch (IOException e) {
            throw new KorykiaiException(e);
        }
    }

    public static Map<String, List<String>> readHashSetFromResource(String resource) {
        return readHashSetFromStream(
                CatalogLoader.class.getResourceAsStream(resource),
                new TypeReference<Map<String, List<String>>>() { });
    }

    public static <K, V> Map<K, V> readHashSetFromResource(String resource, TypeReference<Map<K, V>> ref) {
        return readHashSetFromStream(
                CatalogLoader.class.getResourceAsStream(resource),
                ref);
    }

    public static Map<String, List<String>> readHashSetFromStream(InputStream in) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return mapper.readValue(r, new TypeReference<Map<String, List<String>>>() {});
            }
        } catch (IOException e) {
            throw new KorykiaiException(e);
        }
    }

    public static <K, V> Map<K, V> readHashSet(String json, TypeReference<Map<K, V>> ref) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            try (Reader r = new StringReader(json)) {
                return mapper.readValue(r, ref);
            }
        } catch (IOException e) {
            throw new KorykiaiException(e);
        }
    }
}
