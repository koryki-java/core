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

public class RangeException extends KorykiaiException {

    private static final long serialVersionUID = 5676923577116787823L;

    private Range range;

    public RangeException(Range range) {
        super();
        this.range = range;
    }

    public RangeException(Range range, String msg) {

        super(msg);
        this.range = range;
    }

    public RangeException(Range range, Throwable cause) {

        super(cause);
        this.range = range;
    }


    public RangeException(Range range, String msg, Throwable cause) {

        super(msg, cause);
        this.range = range;
    }

    public Range getRange() {
        return range;
    }

    @Override
    public String toString() {
        return super.toString() + (range != null ? " " + range.toString() : "");
    }

}
