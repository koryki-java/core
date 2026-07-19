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

import java.util.List;

/**
 * Runs a SQL query and returns its rows as positional value lists. Supplied by
 * the engine so a {@link StatTransform} can compute an aggregation in the
 * database without depending on the JDBC/engine layer directly.
 */
@FunctionalInterface
public interface SqlRunner {
    List<List<Object>> run(String sql);
}
