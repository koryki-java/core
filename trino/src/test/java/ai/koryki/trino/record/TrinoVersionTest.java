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

package ai.koryki.trino.record;

import ai.koryki.databases.cases.DatabaseVersionRecorder;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.trino.TrinoUnavailable;
import ai.koryki.trino.northwind.NorthwindTrino;

/** The version of the trino database this suite ran against, for the record of a release. */
@TrinoUnavailable
public class TrinoVersionTest extends DatabaseVersionRecorder {

    @Override
    protected String module() {
        return "trino";
    }

    @Override
    protected String version() throws Exception {
        return "Trino " + first(memory(), "SELECT node_version FROM system.runtime.nodes LIMIT 1");
    }

    /** The memory catalog of the Trino of {@code trino.northwind.url}: same host, same user. */
    private static NorthwindTrino<ListWithSqlResult<HeaderInfo>> memory()
            throws java.sql.SQLException {
        String url =
                System.getProperty("trino.northwind.url")
                        .replaceFirst("(jdbc:trino://[^/]+).*", "$1/memory/default");
        return new NorthwindTrino<>(
                "version",
                System.getProperty("trino.northwind.user"),
                System.getProperty("trino.northwind.password"),
                url);
    }
}
