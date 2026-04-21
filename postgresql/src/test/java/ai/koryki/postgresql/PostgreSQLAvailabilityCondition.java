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
package ai.koryki.postgresql;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Properties;

import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;

public class PostgreSQLAvailabilityCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        final Optional<PostgreSQLUnavailable> optional = findAnnotation(context.getElement(), PostgreSQLUnavailable.class);
        if (optional.isPresent()) {
            final PostgreSQLUnavailable annotation = optional.get();
            try {
                Properties props = new Properties();
                String user = System.getProperty("postgresql.northwind.user");
                props.setProperty("user", user);
                String password = System.getProperty("postgresql.northwind.password");
                props.setProperty("password", password);

                String url = System.getProperty("postgresql.northwind.url");
                DriverManager.getConnection(url, props);

                return ConditionEvaluationResult.enabled("Connection is up");
            } catch (SQLException e) {
                return ConditionEvaluationResult.disabled("Connection is down");
            }
        }
        return ConditionEvaluationResult.enabled("No assumptions, moving on...");
    }
}

