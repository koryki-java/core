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
package ai.koryki.viz.stat;

import ai.koryki.viz.VizColumn;

import java.util.List;

/**
 * The aggregated data a {@link StatTransform} produces for one layer: the
 * computed columns (name + type), the rows, and one or more {@link Part}s —
 * each a mark with its own channel bindings over the shared columns. A
 * histogram is one part (a binned bar); a boxplot is several (whisker rule,
 * box bar, median tick). Rendered as a layer carrying its own inline data.
 */
public class StatResult {

    private final List<Part> parts;
    private final List<VizColumn> columns;
    private final List<List<Object>> rows;

    public StatResult(List<Part> parts, List<VizColumn> columns, List<List<Object>> rows) {
        this.parts = parts;
        this.columns = columns;
        this.rows = rows;
    }

    public List<Part> getParts() {
        return parts;
    }

    public List<VizColumn> getColumns() {
        return columns;
    }

    public List<List<Object>> getRows() {
        return rows;
    }

    /** One mark of a stat layer, with the channels it binds. */
    public static class Part {

        private final String mark;
        private final List<ChannelBinding> bindings;

        public Part(String mark, List<ChannelBinding> bindings) {
            this.mark = mark;
            this.bindings = bindings;
        }

        public String getMark() {
            return mark;
        }

        public List<ChannelBinding> getBindings() {
            return bindings;
        }
    }

    /** Binds a computed column to a visual channel. */
    public static class ChannelBinding {

        private final String channel;
        private final String field;
        private final boolean binned;
        private final String title;

        public ChannelBinding(String channel, String field, boolean binned, String title) {
            this.channel = channel;
            this.field = field;
            this.binned = binned;
            this.title = title;
        }

        public String getChannel() {
            return channel;
        }

        public String getField() {
            return field;
        }

        public boolean isBinned() {
            return binned;
        }

        public String getTitle() {
            return title;
        }
    }
}
