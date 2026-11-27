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

import ai.koryki.antlr.Range;
import ai.koryki.iql.Collector;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.Visitor;
import ai.koryki.iql.query.Join;
import ai.koryki.iql.query.JoinKind;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.RuleContext;

/**
 * Reports a join kind the dialect cannot express: {@code FULL} where {@link
 * SqlDialect#supportsFullJoin()} is false. The renderer refuses the same join as a backstop; this
 * makes it a violation of the query, with a position and before any SQL is built.
 */
public class JoinKindValidator implements Visitor, Collector<List<Violation>> {

    private final List<Violation> violations = new ArrayList<>();
    private final Map<Object, RuleContext> iqlToContext;
    private final SqlDialect dialect;

    public JoinKindValidator(ValidationContext context, SqlDialect dialect) {
        this.iqlToContext = context.iqlToContext();
        this.dialect = dialect;
    }

    @Override
    public boolean visit(Deque<Object> deque, Join join) {
        if (join.getKind() == JoinKind.FULL && !dialect.supportsFullJoin()) {
            violations.add(
                    new Violation(
                            Violation.UNSUPPORTED,
                            join,
                            Range.of(iqlToContext, join),
                            "FULL JOIN is not supported by this database: use + for a LEFT join"
                                    + " and swap the sides for a RIGHT join"));
        }
        return true;
    }

    @Override
    public List<Violation> collect() {
        return violations;
    }
}
