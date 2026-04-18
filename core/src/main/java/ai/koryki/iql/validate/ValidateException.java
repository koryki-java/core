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

import ai.koryki.antlr.KorykiaiException;

import java.util.List;
import java.util.stream.Collectors;

public class ValidateException extends KorykiaiException {

    private List<Violation> violations;

    public ValidateException(List<Violation> violations) {
        this.violations = violations;
    }


    @Override
    public String getMessage() {
        return violations.stream().map(v -> v.toString()).collect(Collectors.joining(System.lineSeparator()));
    }
}
