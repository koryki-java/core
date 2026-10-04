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
package ai.koryki.iql.typing;

import ai.koryki.antlr.KorykiaiException;

/**
 * A selector that does not fit the value it is applied to: a field the struct does not have, a
 * position in a map with text keys, a step into a number. The message is written for the author of
 * the query; {@code SelectorValidator} turns it into a violation with a position.
 */
public class SelectorException extends KorykiaiException {

    private static final long serialVersionUID = 4097418203985551219L;

    public SelectorException(String message) {
        super(message);
    }
}
