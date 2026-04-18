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


public class GrammarException extends PositionException {

    private static final long serialVersionUID = -4825677103798201408L;

    public GrammarException(int line, int pos) {
        super(line, pos);
    }

    public GrammarException(int line, int pos, String msg) {

        super(line, pos, msg);
    }

    public GrammarException(int line, int pos, String msg, Throwable cause) {

        super(line, pos, msg, cause);
    }

}
