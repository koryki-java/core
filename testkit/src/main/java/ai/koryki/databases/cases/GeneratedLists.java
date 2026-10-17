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

package ai.koryki.databases.cases;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Rows of lists made up from a seed, for the engines to answer questions about that a person did
 * not write the answers to: a list that is NULL, empty, of one element or of several, with
 * duplicates, NULL elements and text that is not ASCII. The answers are computed here, from the
 * same rows, so what an engine says is compared with what the rows say and not with another engine.
 *
 * <p>The fault of Oracle that this was written for ({@code docs/NESTED.md}, "Oracle: an element of
 * a VARRAY") depended on the shape of the table and on the rows around the one that was asked, so
 * one hand-written bed did not show it on one table and did on another. Rows like these, loaded in
 * tables of several shapes, are how it is looked for.
 */
public final class GeneratedLists {

    private GeneratedLists() {}

    /** An element of {@code items}. */
    public record Item(String sku, Integer qty) {}

    /** A row: its key and three lists, each of which may be NULL. */
    public record Row(int id, List<String> tags, List<Integer> nums, List<Item> items) {}

    private static final String[] TEXTS = {"a", "b", "c", "d", "a", "O'Brien", "日本", "é"};
    private static final String[] SKUS = {"A-1", "B-2", "C-3", "Ünï"};

    /** The rows with the keys 1 to {@code count}, the same for the same seed. */
    public static List<Row> rows(long seed, int count) {
        Random random = new Random(seed);
        List<Row> rows = new ArrayList<>();
        for (int id = 1; id <= count; id++) {
            rows.add(
                    new Row(
                            id,
                            list(
                                    random,
                                    5,
                                    () -> orNull(random, TEXTS[random.nextInt(TEXTS.length)])),
                            list(random, 6, () -> orNull(random, random.nextInt(26) - 5)),
                            list(
                                    random,
                                    3,
                                    () ->
                                            new Item(
                                                    orNull(
                                                            random,
                                                            SKUS[random.nextInt(SKUS.length)]),
                                                    orNull(random, random.nextInt(10))))));
        }
        return rows;
    }

    /** NULL for one list in ten, empty for one in ten, else one to {@code max} elements. */
    private static <T> List<T> list(Random random, int max, Supplier<T> element) {
        int kind = random.nextInt(10);
        if (kind == 0) {
            return null;
        }
        List<T> list = new ArrayList<>();
        int size = kind == 1 ? 0 : 1 + random.nextInt(max);
        for (int i = 0; i < size; i++) {
            list.add(element.get());
        }
        return list;
    }

    /** NULL one time in ten. */
    private static <T> T orNull(Random random, T value) {
        return random.nextInt(10) == 0 ? null : value;
    }

    // ---- the answers --------------------------------------------------------------------------

    /** A value as the engines hand it over: its text, NULL as null. */
    public static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** The element at a position counted from 1, or from the end when negative; else null. */
    public static <T> T at(List<T> list, int position) {
        if (list == null) {
            return null;
        }
        int index = position > 0 ? position - 1 : list.size() + position;
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    /** The number of elements, or null for a list that is NULL. */
    public static Integer size(List<?> list) {
        return list == null ? null : list.size();
    }

    /** The sum of the elements that are not NULL, or null where there is none. */
    public static Integer sum(List<Integer> list) {
        return list == null
                ? null
                : list.stream()
                        .filter(java.util.Objects::nonNull)
                        .reduce(Integer::sum)
                        .orElse(null);
    }

    public static Integer min(List<Integer> list) {
        return list == null
                ? null
                : list.stream()
                        .filter(java.util.Objects::nonNull)
                        .min(Integer::compare)
                        .orElse(null);
    }

    public static Integer max(List<Integer> list) {
        return list == null
                ? null
                : list.stream()
                        .filter(java.util.Objects::nonNull)
                        .max(Integer::compare)
                        .orElse(null);
    }
}
