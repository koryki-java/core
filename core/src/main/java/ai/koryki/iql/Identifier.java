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

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * TODO add strict/lenient handling for whitespace
 * may add a flag, see {@link #normal(Identifier, String)}
 */
public enum Identifier {
    /** save identifiers in neutral form */
    neutral(false, false),
    /** compare identifiers in normal form */
    normal(false, false),
    /** lowercase is valid for PostgreSQL */
    lowercase(false, true),
    /** quoted is valid for PostgreSQL */
    quoted(true, false),
    /** lowercaseQuoted is valid for PostgreSQL */
    lowercaseQuoted(true, true);

    private final boolean q;
    private final boolean l;

    Identifier(boolean quoted, boolean lower) {
        q = quoted;
        l = lower;
    }

    public boolean isQuoted() {
        return q;
    }

    public boolean isLower() {
        return l;
    }

    /**
     * What SQL will not take as a bare name, on syntax alone.
     *
     * <p>Two kinds. Anything outside {@code [A-Za-z_][A-Za-z0-9_]*} - a space, a hyphen, an
     * umlaut, a leading digit - is the visible kind: {@code FROM Umsatz 2026} is a syntax error
     * anyone can see coming.
     *
     * <p>The other cost a round trip to Snowflake to find: a name that is not all lower case.
     * {@code Betrag} breaks no syntax rule and is no keyword, so nothing about it asks for quotes -
     * but written bare it is folded, to {@code BETRAG} on Snowflake and Oracle and to
     * {@code betrag} on PostgreSQL, and the column it was meant to reach is stored {@code Betrag}.
     * Quoting pins it. Lower case stays unquoted, which is what every existing catalog holds and
     * why no golden moved.
     *
     * <p>Keywords are <em>not</em> judged here. Which words an engine refuses is the engine's
     * property and no two of the eight agree, so it belongs on the dialect - see
     * {@link SqlDialect#isReserved(String)}, whose baseline is {@link #isStandardReserved}.
     */
    public static boolean needsQuoting(String name) {
        if (name == null || name.isEmpty()) {
            return true;
        }
        return !PLAIN.matcher(name).matches()
                || !name.equals(name.toLowerCase(Locale.ROOT));
    }

    /**
     * The SQL-standard reserved words - the baseline every dialect starts from.
     *
     * <p>Deliberately the standard's list and no more. An engine that reserves further words says
     * so itself by overriding {@link SqlDialect#isReserved(String)}: putting Oracle's {@code date}
     * or MariaDB's {@code key} here would quote them on the seven engines that accept them bare,
     * for nothing.
     */
    public static boolean isStandardReserved(String name) {
        return name != null && RESERVED.contains(name.toLowerCase(Locale.ROOT));
    }

    private static final Pattern PLAIN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final Set<String> RESERVED = Set.of(
            "all", "and", "any", "as", "asc", "between", "both", "by", "case", "cast", "check",
            "collate", "column", "constraint", "create", "cross", "current_date", "current_time",
            "current_timestamp", "current_user", "default", "deferrable", "desc", "distinct", "do",
            "else", "end", "except", "exists", "false", "fetch", "filter", "for", "foreign", "from",
            "full", "grant", "group", "having", "in", "initially", "inner", "intersect", "into",
            "is", "join", "lateral", "leading", "left", "like", "limit", "natural", "not", "null",
            "offset", "on", "only", "or", "order", "outer", "over", "overlaps", "partition",
            "placing", "primary", "qualify", "references", "returning", "right", "select",
            "similar", "some", "symmetric", "table", "then", "to", "trailing", "true", "union",
            "unique", "user", "using", "values", "when", "where", "window", "with");

    /**
     * The bare name this form asks for: surrounding double quotes removed, case folded.
     *
     * <p>Split out because the quoting itself is the dialect's business - MariaDB writes backticks
     * and SQL Server brackets, and {@link #normal} only knows the standard's double quote. A
     * renderer therefore folds here and lets {@link SqlDialect#quote} wrap the result.
     *
     * <p>The stripping is defensive: nothing in the catalog arrives quoted, but a name that did
     * would otherwise end up quoted twice. It requires <em>both</em> quotes and a name longer than
     * one character, because the two ends are one pair or they are nothing. Stripping them
     * independently, as this once did, silently truncates a name that merely contains a quote:
     * {@code He said "hi"} became {@code He said "hi} and rendered as a column nobody has. An
     * embedded quote is left alone - {@link SqlDialect#quote} doubles it, which is what every
     * engine expects.
     */
    public static String bare(Identifier i, String id) {
        String raw = id;
        if (raw.length() > 1 && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw = raw.substring(1, raw.length() - 1);
        }
        return i.isLower() ? raw.toLowerCase(Locale.ROOT) : raw;
    }

    public static String normal(Identifier i, String id) {

        if (i.equals(Identifier.neutral)) {
            if (!id.startsWith("\"")) {
                id = id.toUpperCase(Locale.ROOT);
            }
            return id;
        }

        // Case first, quotes second. The other order is what makes the `normal` constant below
        // upper-case the quotes' contents as well.
        String n = i.isLower() ? id.toLowerCase(Locale.ROOT) : id;

        if (i.isQuoted()) {
            String body = n;
            if (n.startsWith("\"")) {
                body = body.substring(1, body.length() - 1);
            }
            if (n.endsWith("\"")) {
                body = body.substring(0, body.length() - 1);
            }
            body = body.replace("\"", "\\\"");
            n = "\"" + body + "\"";
        } else {
            String raw = n;
            if (raw.startsWith("\"")) {
                raw = raw.substring(1);
            }
            if (raw.endsWith("\"")) {
                raw = raw.substring(0, raw.length() - 1);
            }
            boolean forceQuote = forceQuote(raw);
            n = forceQuote ? "\"" + raw + "\"" : raw;
        }

        if (i.equals(Identifier.normal)) {
            if (!n.equals(n.trim())) {

                // TODO add strict / lenient handling for whitespace, see IdentifierEnum
                //throw new IllegalArgumentException("can't normalize id with whitespace: '" + n + "'");
            }
            return n.toUpperCase(Locale.ROOT);
        }

        return n;
    }

    private static final Pattern P = Pattern.compile("\\d+");

    /**
     * An all-digit name has to be quoted even though it holds no special character.
     *
     * <p>{@code \\d+} and not {@code \\d*}: the latter matches the empty string too, so a name that
     * had been reduced to nothing came back as a bare pair of quote characters rather than staying
     * empty. The only callers left are lookup keys (see {@link #normal}), where that pair would
     * have gone into a map key and matched nothing.
     */
    private static boolean forceQuote(String raw) {

        return P.matcher(raw).matches();
    }

    public static String indent(int l) {
        StringBuffer p = new StringBuffer();
        for (int i =  0; i < l; i++) {
            p.append(' ');
        }
        return p.toString();
    }

}
