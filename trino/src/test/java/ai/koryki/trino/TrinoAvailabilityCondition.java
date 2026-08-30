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
package ai.koryki.trino;

import ai.koryki.trino.northwind.NorthwindTrino;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Optional;

public class TrinoAvailabilityCondition implements ExecutionCondition {

    // memoized across the annotated test classes so a down server is probed only once
    private static Boolean available;

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        final Optional<TrinoUnavailable> optional = findAnnotation(context.getElement(), TrinoUnavailable.class);
        if (optional.isPresent()) {
            return available() ? ConditionEvaluationResult.enabled("Connection is up")
                    : ConditionEvaluationResult.disabled("Connection is down");
        }
        return ConditionEvaluationResult.enabled("No assumptions, moving on...");
    }

    private static synchronized boolean available() {
        if (available == null) {
            // the Trino JDBC driver connects lazily — getConnection succeeds without a server,
            // so the probe must execute a statement to actually reach it
            try (Connection c = NorthwindTrino.connection(); Statement s = c.createStatement()) {
                s.executeQuery("SELECT 1").close();
                available = true;
            } catch (Exception e) {
                available = false;
            }
        }
        return available;
    }
}

