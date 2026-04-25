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
package ai.koryki.oracle;

import ai.koryki.oracle.northwind.NorthwindOracle;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Optional;

import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;

public class OracleAvailabilityCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        final Optional<OracleUnavailable> optional = findAnnotation(context.getElement(), OracleUnavailable.class);
        if (optional.isPresent()) {
            final OracleUnavailable annotation = optional.get();
            try {
                NorthwindOracle.connection();
                return ConditionEvaluationResult.enabled("Connection is up");
            } catch (Exception e) {
                return ConditionEvaluationResult.disabled("Connection is down");
            }
        }
        return ConditionEvaluationResult.enabled("No assumptions, moving on...");
    }
}

