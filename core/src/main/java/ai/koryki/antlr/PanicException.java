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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PanicException extends KorykiaiException {

    private static final long serialVersionUID = -6814024223253300450L;

    private List<Interval> panic;

    public PanicException(List<Interval> panic) {
        this.panic = Collections.unmodifiableList(panic);
    }


    @Override
    public String getMessage() {
        return panic.stream().map(v -> v.toString()).collect(Collectors.joining(System.lineSeparator()));
    }

    public List<Interval> getPanic() {
        return panic;
    }
}
