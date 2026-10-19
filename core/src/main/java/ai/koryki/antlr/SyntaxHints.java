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
package ai.koryki.antlr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.antlr.v4.runtime.Vocabulary;

/**
 * What ANTLR's own message cannot say: that a word is a keyword written in the wrong case, or one
 * typo away from one.
 *
 * <p>The keywords are not listed here. They are the grammar's single-word upper-case literals, read
 * from the {@link Vocabulary} of the recognizer that failed, so a keyword added to the grammar is
 * hinted without touching this class, and KQL and IQL each get their own.
 *
 * <p>Two cases, deliberately separate. A word that equals a keyword apart from its case is almost
 * certainly that keyword, so the hint is stated flat ("use FIND"). A word that is merely close is a
 * guess and is worded as one ("did you mean"), and only offered where a guess is plausible: for
 * text the lexer could not recognize at all. A lower-case word the parser rejected is a valid
 * identifier in the wrong place, and a keyword near it would send the writer the wrong way.
 */
public final class SyntaxHints {

    /** Keywords are upper-case words; punctuation and operators like {@code '<='} are not. */
    private static final Pattern KEYWORD = Pattern.compile("[A-Z][A-Z_]*");

    private static final Pattern WORD = Pattern.compile("^[A-Za-z_]{3,}");

    /**
     * A word of at least this many letters may be one edit away from a keyword per three letters.
     */
    private static final int LETTERS_PER_EDIT = 3;

    private SyntaxHints() {}

    /**
     * A suggestion and the sentence that goes with it.
     *
     * @param keyword the keyword that was probably meant
     * @param message the hint, ready to append to an error message
     */
    public record Hint(String keyword, String message) {}

    /** The keywords of the grammar behind {@code vocabulary}, sorted. */
    public static List<String> keywords(Vocabulary vocabulary) {
        List<String> keywords = new ArrayList<>();
        for (int type = 0; type <= vocabulary.getMaxTokenType(); type++) {
            String name = literal(vocabulary, type);
            if (name != null && KEYWORD.matcher(name).matches()) {
                keywords.add(name);
            }
        }
        keywords.sort(Comparator.naturalOrder());
        return keywords;
    }

    /**
     * The literal text of a token type without its quotes, or {@code null} for a token that has no
     * fixed spelling (identifiers, numbers, strings).
     */
    public static String literal(Vocabulary vocabulary, int tokenType) {
        String name = vocabulary.getLiteralName(tokenType);
        if (name == null || name.length() < 2) {
            return null;
        }
        return name.substring(1, name.length() - 1);
    }

    /**
     * The hint for a word the parser did not expect: only a keyword spelled in the wrong case.
     *
     * @return the hint, or {@code null} when the word is not such a keyword
     */
    public static Hint wrongCase(Vocabulary vocabulary, String text) {
        if (text == null) {
            return null;
        }
        String upper = text.toUpperCase(Locale.ROOT);
        if (!text.equals(upper) && keywords(vocabulary).contains(upper)) {
            return new Hint(upper, "keywords are upper-case: use " + upper);
        }
        return null;
    }

    /**
     * The hint for text the lexer could not recognize: a keyword in the wrong case, or the closest
     * keyword when the word is within a typo of it.
     *
     * @param text the unrecognized text; only its leading word is looked at
     * @return the hint, or {@code null} when nothing is close enough to be worth saying
     */
    public static Hint unknownWord(Vocabulary vocabulary, String text) {
        if (text == null) {
            return null;
        }
        var m = WORD.matcher(text);
        if (!m.find()) {
            return null;
        }
        String word = m.group();
        Hint wrongCase = wrongCase(vocabulary, word);
        if (wrongCase != null) {
            return wrongCase;
        }
        String upper = word.toUpperCase(Locale.ROOT);
        int limit = Math.max(1, word.length() / LETTERS_PER_EDIT);
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String keyword : keywords(vocabulary)) {
            int d = distance(upper, keyword);
            if (d <= limit && d < bestDistance) {
                best = keyword;
                bestDistance = d;
            }
        }
        return best == null ? null : new Hint(best, "did you mean " + best + "?");
    }

    /** Levenshtein distance, two rows wide: the words are short and the sets small. */
    public static int distance(String a, String b) {
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
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
