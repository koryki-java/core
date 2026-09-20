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

/**
 * Text constants that apply across the whole project.
 *
 * <p>Lives in {@code ai.koryki.antlr} because that package imports from no other koryki package and
 * is therefore reachable from everywhere — {@code catalog}, {@code jdbc}, {@code iql}, {@code kql}
 * and {@code result} all point here. The other way round would be a package cycle.
 */
public final class Text {

    /**
     * The line break in everything koryki produces — a fixed {@code \n}, not the platform's.
     *
     * <p>SQL, IQL, KQL, CSV and the function documentation are data formats, not console text: they
     * go to database drivers and into golden files. With {@code System.lineSeparator()} the same
     * query would yield different bytes depending on the operating system, and every golden would
     * depend on the machine that produced it.
     *
     * <p>The comparing side assumed {@code \n} anyway: {@code FileAsserter} splits goldens hard on
     * it. Generation and comparison were therefore never in agreement — here they are.
     *
     * <p>The places that <em>look for</em> line breaks belong to this too: {@code KQLFormatter}
     * uses it to tell whether a fragment it produced is multi-line, and {@code IQLSerializer}
     * prefixes every line of a description with {@code //}. Were they looking for something other
     * than what generation writes, they would silently decide wrongly.
     */
    public static final String NL = "\n";

    /**
     * A free-text description rewritten as a line comment — every line of it, including the ones a
     * naive {@code replace(NL, NL + marker)} would miss.
     *
     * <p><b>Why this is not a {@code replace}.</b> The description of a query is its leading
     * comment, so it is author-controlled text — a KQL block comment on the way in, a SQL or IQL
     * line comment on the way out. A block comment holds any character the lexer's {@code .*?}
     * accepts, and that includes a bare carriage return. Prefixing only after {@code \n} therefore
     * left a CR sitting inside the emitted comment — and a CR ends a line comment on every engine
     * here. Measured on DuckDB: a comment line broken by a CR and continued with {@code SELECT 42
     * AS injected} answers a column named {@code injected}. Everything the author wrote after that
     * CR was SQL, standing in front of the generated query.
     *
     * <p>{@code \R} is therefore the splitting rule and not {@code \n}: it is every line boundary
     * Java knows — CR, LF, CRLF, NEL, the two Unicode separators, and the vertical whitespace. A
     * couple of those no SQL engine treats as a line end; commenting them out anyway costs a
     * description a line break it did not ask for, which is the cheap side of the trade.
     */
    public static String lineComment(String marker, String description) {
        return marker + description.replaceAll("\\R", NL + marker);
    }

    private Text() {}
}
