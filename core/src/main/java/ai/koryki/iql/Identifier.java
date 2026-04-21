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

import java.util.regex.Pattern;

/**
 * TODO add strict/lenient handling for whitespace
 * may add a flag, see Identifier#normal
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

    public static String unquote(String id) {
        String n = id;
        String raw = n;
        if (raw.startsWith("\'")) {
            raw = raw.substring(1);
        }
        if (raw.endsWith("\'")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        return raw;
    }


    public static String normal(Identifier i, String id) {

        if (i.equals(Identifier.neutral)) {
            if (!id.startsWith("\"")) {
                id = id.toUpperCase();
            }
            return id;
        }

        String n = i.isLower() ? id.toLowerCase() : id;

        if (i.isQuoted()) {
            if (!n.startsWith("\"")) {
                n = "\"" + n;
            }
            if (!n.endsWith("\"")) {
                n =  n + "\"";
            }
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
            return n.toUpperCase();
        }

        return n;
    }

    private static final Pattern P = Pattern.compile("\\d*");

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
