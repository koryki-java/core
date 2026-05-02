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

public class Violation {

    private String type;
    private String message;
    private Object iql;
    private Range range;
    private Range related;

    public Violation(String type, Object iql, Range range, String message) {
        this(type, iql, range, message, null);
    }


    public Violation(String type, Object iql, Range range, String message, Range related) {
        this.iql = iql;
        this.range = range;
        this.related = related;
        this.message = message;
    }

    public Object getIql() {
        return iql;
    }

    public void setIql(Object iql) {
        this.iql = iql;
    }

    public Range getRange() {
        return range;
    }

    public void setRange(Range range) {
        this.range = range;
    }

    public Range getRelated() {
        return related;
    }

    public void setRelated(Range related) {
        this.related = related;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return type + ": " + iql.getClass().getSimpleName() + " [" + range + "]: " + getMessage() + (related != null ? " related [" + related + "]" : "");
    }

    public String getType() {
        return type;
    }
}
