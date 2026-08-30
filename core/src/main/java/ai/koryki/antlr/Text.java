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
 * <p>Lives in {@code ai.koryki.antlr} because that package imports from no other koryki package
 * and is therefore reachable from everywhere — {@code catalog}, {@code jdbc}, {@code iql},
 * {@code kql} and {@code result} all point here. The other way round would be a package cycle.
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
     * <p>The places that <em>look for</em> line breaks belong to this too: {@code KQLFormatter} uses
     * it to tell whether a fragment it produced is multi-line, and {@code IQLSerializer} prefixes
     * every line of a description with {@code //}. Were they looking for something other than what
     * generation writes, they would silently decide wrongly.
     */
    public static final String NL = "\n";

    private Text() {
    }
}
