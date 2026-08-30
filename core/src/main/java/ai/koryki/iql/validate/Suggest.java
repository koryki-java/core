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
package ai.koryki.iql.validate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * What the author probably meant, for a name the catalog does not have.
 *
 * <p>Naming the mistake is half a message; the other half is the way out. The validators hold the
 * right names at the moment they reject a wrong one, so the comparison belongs here rather than in
 * a consumer that reconstructs it from the message prose afterwards.
 *
 * <p>Every caller should hand in the <em>narrowest</em> candidate set it knows to be valid at that
 * position — the attributes of the one entity the alias resolves to, not every attribute in the
 * model; the links between the two entities actually being joined, not every link. A suggestion
 * drawn from a wider set is worse than none: it reads as authoritative and sends its reader, human
 * or model, confidently in the wrong direction.
 *
 * <p>Public because the same question is asked outside validation: a tool that looks an entity up
 * by name has to answer "no such entity" with the same help, and measuring it a second time in
 * another module is how the two answers drift apart. {@link #hint} is public for that same reason
 * one level up: a reporter with no {@link Violation} to hang the names on — one that throws where
 * the validators collect — would otherwise spell the prose its own way, and that is the same drift
 * in the wording as the other would be in the numbers.
 *
 * <p>Whoever <em>does</em> have a Violation passes the names to {@link Violation#suggesting} and
 * does not call {@link #hint} as well: {@code suggesting} already appends it, and the two together
 * print the suggestion twice.
 */
public final class Suggest {

    /** How many names a message will carry. Beyond three it stops reading as a hint. */
    private static final int MAX = 3;

    private Suggest() {
    }

    /**
     * Case and separators removed, so the comparison sees the name and not its spelling convention.
     *
     * <p>{@code orderId}, {@code order_id}, {@code ORDER-ID} and {@code orderid} all reduce to
     * {@code orderid} and therefore measure as the same name at distance zero. Lowercasing alone
     * already folds camelCase against snake_case once the separators are gone, so there is no need
     * to split words apart.
     *
     * <p>This decides only how close two names <em>score</em>. It does not make any of those
     * spellings valid: the model name is the only one KQL accepts, and the others are still
     * errors — errors that now say which spelling to use.
     */
    public static String normalize(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                b.append(c);
            }
        }
        return b.toString();
    }

    /**
     * Damerau-Levenshtein, in its optimal-string-alignment form: three rows wide.
     *
     * <p>Plain Levenshtein charges two edits for a transposition, so {@code nmae} sits as far from
     * {@code name} as an unrelated word does and drops below the threshold — yet swapping adjacent
     * keys is the typo people actually make. The one extra arm below charges it as the single
     * mistake it is.
     *
     * <p>The unrestricted form of the metric additionally permits edits <em>between</em> a
     * transposed pair, which changes the answer only for inputs like {@code CA} against
     * {@code ABC}. Identifiers do not go wrong that way, and the restricted form needs no
     * alphabet-indexed table, so it stays a handful of lines.
     */
    public static int distance(String a, String b) {
        int[] beforePrevious = new int[b.length() + 1];
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitute = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), substitute);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    current[j] = Math.min(current[j], beforePrevious[j - 2] + 1);
                }
            }
            int[] swap = beforePrevious;
            beforePrevious = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    /**
     * How far a name of this length may stray and still count as a typo.
     *
     * <p>The bound has to grow with the name, because two edits in {@code id} leave nothing of the
     * original while two edits in {@code ship_postal_code} are plainly still the same word. The
     * previous bound — {@code max(2, length / 3)} — had it the wrong way round at the short end: a
     * floor of two admits <em>every</em> two-character candidate, since no two strings that short
     * are more than two edits apart, so {@code id} would have offered {@code no}.
     */
    private static int limit(int length) {
        if (length <= 4) {
            return 1;
        }
        return length <= 8 ? 2 : 3;
    }

    /**
     * The closest of {@code candidates} to {@code wrong}, nearest first, at most three.
     *
     * <p>Ties break on the name so the order is stable: the same query must not produce a different
     * message on a different run, or the goldens that pin these messages would flicker.
     */
    public static List<String> closest(String wrong, Collection<String> candidates) {
        if (wrong == null || wrong.isBlank() || candidates == null) {
            return List.of();
        }
        String w = normalize(wrong);
        if (w.isEmpty()) {
            return List.of();
        }
        int limit = limit(w.length());
        record Scored(String name, int distance) { }
        List<Scored> scored = new ArrayList<>();
        for (String c : candidates) {
            if (c == null) {
                continue;
            }
            int d = distance(w, normalize(c));
            if (d <= limit) {
                scored.add(new Scored(c, d));
            }
        }
        scored.sort(Comparator.comparingInt(Scored::distance).thenComparing(Scored::name));
        return scored.stream().map(Scored::name).distinct().limit(MAX).toList();
    }

    /**
     * The suggestions as the tail of a message, or nothing at all when there are none.
     *
     * <p>Written to be appended: it carries its own leading separator, so a caller with no
     * suggestions concatenates an empty string and the message reads exactly as it did before.
     *
     * <p>{@link Violation#suggesting} is the usual way in and appends this for you. Reach for
     * {@code hint} directly only where there is no Violation to carry the names — and then never
     * for a message that has already been through {@code suggesting}.
     */
    public static String hint(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder(" — did you mean ");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                b.append(i == names.size() - 1 ? " or " : ", ");
            }
            b.append('\'').append(names.get(i)).append('\'');
        }
        return b.append('?').toString();
    }
}
