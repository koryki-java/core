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
package ai.koryki.viz;

/**
 * Outcome of {@code Engine.validateAndRender} — the deterministic check for a
 * generate → validate → fix loop. Exactly one of three states:
 * <ul>
 *   <li><b>chart</b>: {@code spec} set, no error — a valid VISUALISE clause;</li>
 *   <li><b>no chart</b>: both null — a valid query <i>without</i> a VISUALISE clause,
 *       which is fine (not every query has a meaningful chart);</li>
 *   <li><b>error</b>: {@code error} set — the precise message to feed back to the
 *       generator/LLM for repair (parse, validation, or render failure).</li>
 * </ul>
 *
 * <p>The {@code spec} is built structurally from the query's resolved columns with
 * <b>no SQL executed</b> (empty data). It proves the clause compiles; the real
 * data-filled spec comes from {@code Engine.executeVegaLite}.
 */
public record RenderResult(String spec, String error) {

    public static RenderResult chart(String spec) {
        return new RenderResult(spec, null);
    }

    public static RenderResult noChart() {
        return new RenderResult(null, null);
    }

    public static RenderResult error(String message) {
        return new RenderResult(null, message);
    }

    /** True when there was no error — a chart, or a deliberate no-chart. */
    public boolean ok() {
        return error == null;
    }

    /** True when a chart spec was produced. */
    public boolean hasChart() {
        return spec != null;
    }
}
