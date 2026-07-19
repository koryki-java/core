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
package ai.koryki.iql.query.viz;

/**
 * A single aesthetic mapping inside a VISUALISE / MAPPING list.
 *
 * <ul>
 *   <li>{@code col AS x}   → {@link #column}="col", {@link #channel}="x"</li>
 *   <li>{@code 'red' AS fill} → {@link #literal}="red", {@link #channel}="fill"</li>
 *   <li>{@code x}          → implicit, {@link #column}="x", {@link #channel}="x"</li>
 *   <li>{@code *}          → {@link #wildcard}=true</li>
 * </ul>
 * The {@code column} refers to a query output-column alias (a FETCH header).
 */
public class Mapping {

    private boolean wildcard;
    private String column;
    private Object literal;
    private String channel;

    public boolean isWildcard() {
        return wildcard;
    }

    public void setWildcard(boolean wildcard) {
        this.wildcard = wildcard;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public Object getLiteral() {
        return literal;
    }

    public void setLiteral(Object literal) {
        this.literal = literal;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }
}
