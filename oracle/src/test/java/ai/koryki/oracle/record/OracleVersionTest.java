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

package ai.koryki.oracle.record;

import ai.koryki.databases.cases.DatabaseVersionRecorder;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.oracle.OracleUnavailable;
import ai.koryki.oracle.northwind.NorthwindOracle;

/** The version of the oracle database this suite ran against, for the record of a release. */
@OracleUnavailable
public class OracleVersionTest extends DatabaseVersionRecorder {

    @Override
    protected String module() {
        return "oracle";
    }

    @Override
    protected String version() throws Exception {
        var database = new NorthwindOracle<ListWithSqlResult<HeaderInfo>>();
        try {
            return "Oracle Database "
                    + first(
                            database,
                            "SELECT version_full FROM product_component_version WHERE ROWNUM = 1");
        } catch (RuntimeException older) {
            return first(database, "SELECT banner FROM v$version WHERE ROWNUM = 1");
        }
    }
}
