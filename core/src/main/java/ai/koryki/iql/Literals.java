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
package ai.koryki.iql;

import ai.koryki.antlr.KorykiaiException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Literal formatting shared by the SQL renderers and the IQL serializer, so the two never drift.
 */
final class Literals {

    private Literals() {}

    /**
     * The SQL string literal for a KQL/IQL {@code SQ_STRING} token — delimiters included, as the
     * token itself arrives.
     *
     * <p><b>Why this is not {@code token.replace("\\'", "''")}.</b> That was the previous
     * implementation, and it ran over the token <em>with its delimiters</em>, which let it read a
     * backslash sitting just before the closing quote as the start of an escape. The lexer does not
     * read it that way. {@code SQ_STRING : SINGLE_QUOTE ('\\'' | .)*? SINGLE_QUOTE} prefers the
     * escape, but only where the escape reading yields a token at all; where it does not, the
     * backslash is an ordinary character and the quote after it closes the literal. So for the KQL
     * {@code 'a\'} the lexer says "the value is {@code a\}" and the old replacement said "the value
     * is {@code a} and the literal is still open" — and emitted {@code 'a''}, an unterminated
     * literal that swallows whatever the renderer wrote next.
     *
     * <p>A valid query rendering SQL that does not parse is a defect on its own. It is also the
     * exact shape an escaped-wrongly value takes before it is anything worse, which is why the two
     * readings are reconciled here rather than patched at the one call site: strip the delimiters
     * first, unescape what is inside, escape it again for SQL, and put the delimiters back. The
     * body is then the only thing either step ever looks at, and a delimiter cannot be mistaken for
     * content.
     *
     * <p>Found by {@code HostileLiteralTest#anyLiteralThatParsesRendersContained}, on generated
     * input, at a shape no enumerated case had.
     */
    static String text(String token) {
        // The lexer cannot produce a token without both delimiters, so this is total.
        String body = token.substring(1, token.length() - 1);
        String value = body.replace("\\'", "'");
        return "'" + value.replace("'", "''") + "'";
    }

    /**
     * Canonical text form of a numeric literal: integers verbatim, decimals with trailing zeros
     * dropped (0.0 → 0) but full precision kept. Used by both {@code SqlSelectRenderer} (→ SQL) and
     * {@code IQLSerializer} (→ IQL); the surrounding quoting/typing differs, this core does not.
     */
    static String number(Number n) {
        if (n instanceof BigInteger bigInteger) {
            return bigInteger.toString();
        } else if (n instanceof BigDecimal bigDecimal) {
            return bigDecimal.stripTrailingZeros().toPlainString();
        } else {
            throw new KorykiaiException("unsupported number type: " + n.getClass());
        }
    }
}
