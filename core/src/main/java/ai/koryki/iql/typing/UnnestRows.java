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

package ai.koryki.iql.typing;

import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptor;
import java.util.ArrayList;
import java.util.List;

/**
 * The columns of a list made into rows. An element that is a struct has its fields, so {@code
 * i.sku} is the field {@code sku} of the element; any other element is the one column {@code
 * value}; and every row has its {@code ordinal}, the position in the list from 1.
 *
 * <p>The fields are looked up by their physical keys: what a model calls them is translated before
 * this is asked, as it is for a selector.
 */
public final class UnnestRows {

    /** The column of an element that is not a struct. */
    public static final String VALUE = "value";

    /** The position of the element in its list, from 1. */
    public static final String ORDINAL = "ordinal";

    private UnnestRows() {}

    /** The type of an element of the list, or null where it is not a list. */
    public static TypeDescriptor elementOf(TypeDescriptor list) {
        return list != null && CoreTypeFamily.ARRAY.equals(list.getTypeFamily())
                ? list.element()
                : null;
    }

    private static boolean struct(TypeDescriptor element) {
        return CoreTypeFamily.STRUCT.equals(element.getTypeFamily());
    }

    /** The columns, in order: the fields of the struct, or {@code value}; then {@code ordinal}. */
    public static List<String> columns(TypeDescriptor element) {
        List<String> names =
                new ArrayList<>(struct(element) ? element.fieldNames() : List.of(VALUE));
        names.add(ORDINAL);
        return names;
    }

    /** The type of the column, or null where the rows have none of that name. */
    public static TypeDescriptor type(TypeDescriptor element, String column) {
        if (ORDINAL.equals(column)) {
            return TypeDescriptor.INTEGER;
        }
        if (struct(element)) {
            return element.field(column).orElse(null);
        }
        return VALUE.equals(column) ? element : null;
    }
}
