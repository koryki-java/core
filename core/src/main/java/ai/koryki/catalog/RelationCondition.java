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
package ai.koryki.catalog;

import ai.koryki.catalog.schema.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The condition of a {@link ai.koryki.catalog.schema.Relation} that is not a list of equal columns:
 * a conjunction of comparisons between a column of the relation's start table and a column of its
 * end table.
 *
 * <p>Written as text in the catalog: {@code start.sal BETWEEN end.losal AND end.hisal}, or {@code
 * start.mgr = end.empno AND start.sal < end.sal}. A comparison is {@code <ref> <op> <ref>} with
 * {@code op} one of {@code = <> != < <= > >=}, a reference is {@code start.<column>} or {@code
 * end.<column>}, and {@code BETWEEN} is shorthand for two comparisons. Terms are joined with {@code
 * AND}; nothing else is accepted -- no {@code OR}, no functions, no literals, no sub-queries. That
 * keeps a relation something a catalog can be checked for and a cardinality can be reasoned about.
 *
 * <p>The columns are the physical column names of the two tables, as in {@link
 * ai.koryki.catalog.schema.Relation#getStartColumns()}.
 */
public final class RelationCondition {

    /** One side of a comparison: a column of the start or of the end table. */
    public record Ref(boolean start, String column) {
        Ref flip() {
            return new Ref(!start, column);
        }

        @Override
        public String toString() {
            return (start ? "start." : "end.") + column;
        }
    }

    /** {@code left op right}; the operator is one of {@code = <> < <= > >=}. */
    public record Term(Ref left, String op, Ref right) {
        @Override
        public String toString() {
            return left + " " + op + " " + right;
        }
    }

    private static final String REF = "(start|end)\\.([A-Za-z_][A-Za-z0-9_$]*)";
    private static final Pattern TERM =
            Pattern.compile(
                    "\\s*"
                            + REF
                            + "\\s*(?:(<>|!=|<=|>=|=|<|>)\\s*"
                            + REF
                            + "|\\s(?i:BETWEEN)\\s+"
                            + REF
                            + "\\s+(?i:AND)\\s+"
                            + REF
                            + ")\\s*");
    private static final Pattern AND = Pattern.compile("(?i:AND)\\s+");

    private final List<Term> terms;

    private RelationCondition(List<Term> terms) {
        this.terms = List.copyOf(terms);
    }

    public List<Term> terms() {
        return terms;
    }

    /**
     * Reads a condition.
     *
     * @throws IllegalArgumentException with the offending part named, when the text is not of the
     *     form described in the class comment
     */
    public static RelationCondition parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("relation condition: empty");
        }
        List<Term> terms = new ArrayList<>();
        Matcher m = TERM.matcher(text);
        int pos = 0;
        while (true) {
            m.region(pos, text.length());
            if (!m.lookingAt()) {
                throw new IllegalArgumentException(
                        "relation condition: cannot read '"
                                + text.substring(pos).strip()
                                + "' -- expected 'start.a <op> end.b' or 'start.a BETWEEN end.b AND"
                                + " end.c', joined with AND");
            }
            Ref left = new Ref(m.group(1).equals("start"), m.group(2));
            if (m.group(3) != null) {
                String op = m.group(3).equals("!=") ? "<>" : m.group(3);
                terms.add(new Term(left, op, new Ref(m.group(4).equals("start"), m.group(5))));
            } else {
                terms.add(new Term(left, ">=", new Ref(m.group(6).equals("start"), m.group(7))));
                terms.add(new Term(left, "<=", new Ref(m.group(8).equals("start"), m.group(9))));
            }
            pos = m.end();
            if (pos >= text.length()) {
                return new RelationCondition(terms);
            }
            Matcher and = AND.matcher(text);
            and.region(pos, text.length());
            if (!and.lookingAt()) {
                throw new IllegalArgumentException(
                        "relation condition: expected AND before '"
                                + text.substring(pos).strip()
                                + "'");
            }
            pos = and.end();
        }
    }

    /** The same condition seen from the other table: {@code start} and {@code end} swapped. */
    public RelationCondition flipped() {
        List<Term> t = new ArrayList<>();
        for (Term term : terms) {
            t.add(new Term(term.left().flip(), term.op(), term.right().flip()));
        }
        return new RelationCondition(t);
    }

    /** The canonical text: one comparison per term, {@code BETWEEN} expanded. */
    public String text() {
        return String.join(" AND ", terms.stream().map(Term::toString).toList());
    }

    @Override
    public String toString() {
        return text();
    }

    /**
     * Checks that every column named is a column of the table on its side.
     *
     * @return the problems, empty when the condition fits
     */
    public List<String> check(Table start, Table end) {
        List<String> problems = new ArrayList<>();
        for (Term t : terms) {
            for (Ref r : List.of(t.left(), t.right())) {
                Table table = r.start() ? start : end;
                if (table.getColumns().stream()
                        .noneMatch(c -> c.getName().equalsIgnoreCase(r.column()))) {
                    problems.add(
                            "no column '"
                                    + r.column()
                                    + "' in table "
                                    + table.getName()
                                    + " ("
                                    + r
                                    + ")");
                }
            }
        }
        return problems;
    }
}
