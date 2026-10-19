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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.FailedPredicateException;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * ANTLR's recovery, with messages a writer can act on.
 *
 * <p>The recovery itself is the default one and is not touched; only the sentences change. ANTLR
 * words an error with the grammar's internals: {@code mismatched input 'FETCH' expecting {'AND',
 * SELECTOR_INT, SELECTOR_ID, '-', '+'}}. Here the same error reads {@code unexpected 'FETCH';
 * expected AND, '+', '-', '@index'}: keywords bare, punctuation quoted, and a token without a fixed
 * spelling by what it is, not by what the grammar calls it.
 *
 * <p>A list longer than {@link #MAX_EXPECTED} is not a help, it is a dump of everything that could
 * start an expression, so the message then only names what was found.
 */
public class FriendlyErrorStrategy extends DefaultErrorStrategy {

    /** More alternatives than this are not named. */
    static final int MAX_EXPECTED = 8;

    private static final Pattern KEYWORD = Pattern.compile("[A-Z][A-Z_]*");

    /** What the grammar-independent tokens are called when they are not spelled. */
    private static final Map<String, String> COMMON =
            Map.of(
                    "ID", "a name",
                    "INT", "an integer",
                    "NUMBER", "a number",
                    "STRING", "a string",
                    "SQ_STRING", "a string",
                    "EOF", "end of query");

    private final Map<String, String> names;

    /** Without grammar-specific names; a symbolic token not known here is shown lower-cased. */
    public FriendlyErrorStrategy() {
        this(Map.of());
    }

    /**
     * @param names what the grammar's symbolic tokens are called for a writer, by token name; they
     *     take precedence over the common names
     */
    public FriendlyErrorStrategy(Map<String, String> names) {
        this.names = names;
    }

    @Override
    protected void reportInputMismatch(Parser recognizer, InputMismatchException e) {
        recognizer.notifyErrorListeners(
                e.getOffendingToken(),
                unexpected(recognizer, e.getOffendingToken(), e.getExpectedTokens()),
                e);
    }

    @Override
    protected void reportNoViableAlternative(Parser recognizer, NoViableAltException e) {
        recognizer.notifyErrorListeners(
                e.getOffendingToken(),
                unexpected(recognizer, e.getOffendingToken(), e.getExpectedTokens()),
                e);
    }

    @Override
    protected void reportFailedPredicate(Parser recognizer, FailedPredicateException e) {
        recognizer.notifyErrorListeners(e.getOffendingToken(), "rule " + e.getMessage(), e);
    }

    @Override
    protected void reportMissingToken(Parser recognizer) {
        if (inErrorRecoveryMode(recognizer)) {
            return;
        }
        beginErrorCondition(recognizer);
        Token t = recognizer.getCurrentToken();
        String expected = expected(recognizer, getExpectedTokens(recognizer));
        String msg =
                expected == null
                        ? "unexpected " + display(t)
                        : "missing " + expected + " before " + display(t);
        recognizer.notifyErrorListeners(t, msg, null);
    }

    @Override
    protected void reportUnwantedToken(Parser recognizer) {
        if (inErrorRecoveryMode(recognizer)) {
            return;
        }
        beginErrorCondition(recognizer);
        Token t = recognizer.getCurrentToken();
        recognizer.notifyErrorListeners(
                t, unexpected(recognizer, t, getExpectedTokens(recognizer)), null);
    }

    private String unexpected(Parser recognizer, Token found, IntervalSet expectedTokens) {
        String expected = expected(recognizer, expectedTokens);
        return expected == null
                ? "unexpected " + display(found)
                : "unexpected " + display(found) + "; expected " + expected;
    }

    private static String display(Token t) {
        if (t == null || t.getType() == Token.EOF) {
            return "end of query";
        }
        return "'"
                + t.getText().replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
                + "'";
    }

    /**
     * The alternatives as one phrase, or {@code null} when they are too many to be a help. "one of"
     * is left out of a pair, where "A or B" says it.
     */
    private String expected(Parser recognizer, IntervalSet expectedTokens) {
        if (expectedTokens == null || expectedTokens.isNil()) {
            return null;
        }
        Vocabulary vocabulary = recognizer.getVocabulary();
        List<String> parts = new ArrayList<>();
        for (int type : expectedTokens.toArray()) {
            String part = name(vocabulary, type);
            if (part != null && !parts.contains(part)) {
                parts.add(part);
            }
        }
        if (parts.isEmpty() || parts.size() > MAX_EXPECTED) {
            return null;
        }
        Collections.sort(parts);
        if (parts.size() == 1) {
            return parts.get(0);
        }
        String last = parts.remove(parts.size() - 1);
        return String.join(", ", parts) + (parts.size() == 1 ? " or " : ", or ") + last;
    }

    private String name(Vocabulary vocabulary, int type) {
        String symbolic = vocabulary.getSymbolicName(type);
        if (symbolic != null) {
            String mapped = names.get(symbolic);
            if (mapped == null) {
                mapped = COMMON.get(symbolic);
            }
            if (mapped != null) {
                return mapped;
            }
        }
        String literal = SyntaxHints.literal(vocabulary, type);
        if (literal != null) {
            return KEYWORD.matcher(literal).matches() ? literal : "'" + literal + "'";
        }
        return symbolic == null
                ? null
                : symbolic.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }
}
