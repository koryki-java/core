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
package ai.koryki.catalog.schema;

/**
 * What a catalog says about one leaf inside a nested column, beyond what the type spelling can: the
 * family and the encoding. {@code "[].ts"} with encoding {@code INSTANT} makes the {@code ts} of a
 * list of structs a zone-aware timestamp. The path is a {@link ai.koryki.catalog.types.TypePath}
 * from the column down.
 */
public class NestedType {

    private String path;
    private String typeFamily;
    private String typeEncoding;

    public NestedType() {}

    public NestedType(String path, String typeFamily, String typeEncoding) {
        this.path = path;
        this.typeFamily = typeFamily;
        this.typeEncoding = typeEncoding;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getTypeFamily() {
        return typeFamily;
    }

    public void setTypeFamily(String typeFamily) {
        this.typeFamily = typeFamily;
    }

    public String getTypeEncoding() {
        return typeEncoding;
    }

    public void setTypeEncoding(String typeEncoding) {
        this.typeEncoding = typeEncoding;
    }
}
