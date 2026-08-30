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
package ai.koryki.snowflake.covid19;

import ai.koryki.jdbc.ResultProcessor;
import ai.koryki.snowflake.SnowflakeDatabase;

import java.sql.Connection;
import java.sql.SQLException;

public class Covid19Database<C extends ResultProcessor<?>> extends SnowflakeDatabase<C> {

    public Covid19Database() throws Exception {
        this("covid19", connection());
    }

    public Covid19Database(String name, Connection conn) {
        super(name, conn);
    }

    public static Connection connection() throws Exception {

        return connection(
            System.getProperty("snowflake.covid19.user"),
            System.getProperty("snowflake.covid19.url") );
    }
}
