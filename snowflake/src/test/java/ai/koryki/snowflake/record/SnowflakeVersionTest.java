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

package ai.koryki.snowflake.record;

import ai.koryki.databases.cases.DatabaseVersionRecorder;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.snowflake.SnowflakeUnavailable;
import ai.koryki.snowflake.northwind.NorthwindSnowflake;

/** The version of the snowflake database this suite ran against, for the record of a release. */
@SnowflakeUnavailable
public class SnowflakeVersionTest extends DatabaseVersionRecorder {

    @Override
    protected String module() {
        return "snowflake";
    }

    @Override
    protected String version() throws Exception {
        return "Snowflake "
                + first(
                        new NorthwindSnowflake<ListWithSqlResult<HeaderInfo>>(),
                        "SELECT CURRENT_VERSION()");
    }
}
