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

import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Entry;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * How an {@link Entry} is written in a {@code db.json}: a string for a reference ({@code
 * "VARCHAR"}, {@code "latitude"}), an object for a concept of its own. Register it on every mapper
 * that reads or writes a schema; {@link CatalogLoader} and {@link Util} do.
 */
public class SchemaModule extends SimpleModule {

    public SchemaModule() {
        super("koryki-schema");
        addSerializer(Entry.class, new EntrySerializer());
        addDeserializer(Entry.class, new EntryDeserializer());
    }

    private static final class EntrySerializer extends ValueSerializer<Entry> {
        @Override
        public void serialize(Entry entry, JsonGenerator generator, SerializationContext context) {
            if (entry.isInline()) {
                context.writeValue(generator, entry.getConcept());
            } else {
                generator.writeString(entry.getReference());
            }
        }
    }

    private static final class EntryDeserializer extends ValueDeserializer<Entry> {
        @Override
        public Entry deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() == JsonToken.VALUE_STRING) {
                return Entry.of(parser.getString());
            }
            return Entry.of(context.readValue(parser, Concept.class));
        }
    }
}
