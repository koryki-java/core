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
package ai.koryki.iql.functions;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Declarative rendering for the common case of a dialect function mapping:
 * a SQL string with argument placeholders.
 *
 * <ul>
 *   <li>{@code {0}}, {@code {1}}, … — the n-th argument, rendered</li>
 *   <li>{@code {*}} — all arguments, rendered and comma-joined</li>
 *   <li>{@code {2*}} — arguments from index 2 on, rendered and comma-joined</li>
 * </ul>
 *
 * Examples: {@code POSITION({0} IN {1})}, {@code INSTR({1}, {0})},
 * {@code CAST({0} AS DECIMAL({1}, {2}))}, {@code COALESCE({*})},
 * {@code CURRENT_TIMESTAMP}.
 *
 * <p>The template is parsed once at construction. Referencing an argument index
 * beyond the call's argument list fails at render time; arity should be guarded
 * by the owning definition's {@link FunctionSignature}.
 */
public final class SqlTemplate {

    /** One parsed template piece: either literal SQL or a placeholder. */
    private sealed interface Segment permits Literal, Arg, Rest {}
    private record Literal(String text) implements Segment {}
    private record Arg(int index) implements Segment {}
    private record Rest(int from) implements Segment {}

    private final String template;
    private final List<Segment> segments;

    public SqlTemplate(String template) {
        this.template = template;
        this.segments = parse(template);
    }

    private static List<Segment> parse(String template) {
        List<Segment> segments = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '{') {
                int close = template.indexOf('}', i);
                if (close < 0) {
                    throw new KorykiaiException("unclosed placeholder in template: " + template);
                }
                String body = template.substring(i + 1, close);
                Segment placeholder = placeholder(template, body);
                if (!literal.isEmpty()) {
                    segments.add(new Literal(literal.toString()));
                    literal.setLength(0);
                }
                segments.add(placeholder);
                i = close + 1;
            } else {
                literal.append(c);
                i++;
            }
        }
        if (!literal.isEmpty()) {
            segments.add(new Literal(literal.toString()));
        }
        return List.copyOf(segments);
    }

    private static Segment placeholder(String template, String body) {
        if (body.equals("*")) {
            return new Rest(0);
        }
        try {
            if (body.endsWith("*")) {
                return new Rest(Integer.parseInt(body.substring(0, body.length() - 1)));
            }
            return new Arg(Integer.parseInt(body));
        } catch (NumberFormatException e) {
            throw new KorykiaiException("invalid placeholder {" + body + "} in template: " + template);
        }
    }

    public String render(SqlSelectRenderer renderer, Function function, int indent) {
        List<Expression> args = function.getArguments();
        StringBuilder b = new StringBuilder();
        for (Segment s : segments) {
            if (s instanceof Literal l) {
                b.append(l.text());
            } else if (s instanceof Arg a) {
                if (a.index() >= args.size()) {
                    throw new KorykiaiException(function.getFunc() + ": template references argument {"
                            + a.index() + "} but the call has " + args.size() + " arguments");
                }
                b.append(renderer.toSql(args.get(a.index()), indent));
            } else if (s instanceof Rest r) {
                b.append(args.stream().skip(r.from())
                        .map(e -> renderer.toSql(e, indent))
                        .collect(Collectors.joining(", ")));
            }
        }
        return b.toString();
    }

    /**
     * Fills the placeholders with already-rendered argument strings. Used to
     * drive operator rendering from a definition's template while the operands
     * are rendered upstream (e.g. through comparison encoding reconciliation),
     * rather than via {@link #render} which renders the arguments itself.
     */
    public String fill(List<String> args) {
        StringBuilder b = new StringBuilder();
        for (Segment s : segments) {
            if (s instanceof Literal l) {
                b.append(l.text());
            } else if (s instanceof Arg a) {
                if (a.index() >= args.size()) {
                    throw new KorykiaiException("template " + template + " references argument {"
                            + a.index() + "} but only " + args.size() + " were provided");
                }
                b.append(args.get(a.index()));
            } else if (s instanceof Rest r) {
                b.append(String.join(", ", args.subList(Math.min(r.from(), args.size()), args.size())));
            }
        }
        return b.toString();
    }

    /**
     * Renders the template with symbolic argument names instead of real
     * expressions — used by the documentation generator to show a dialect's
     * rendering without needing a live renderer.
     */
    public String preview(List<String> argNames) {
        StringBuilder b = new StringBuilder();
        for (Segment s : segments) {
            if (s instanceof Literal l) {
                b.append(l.text());
            } else if (s instanceof Arg a) {
                b.append(a.index() < argNames.size() ? argNames.get(a.index()) : "arg" + a.index());
            } else if (s instanceof Rest r) {
                b.append(String.join(", ", argNames.subList(Math.min(r.from(), argNames.size()), argNames.size())));
            }
        }
        return b.toString();
    }

    @Override
    public String toString() {
        return template;
    }
}
