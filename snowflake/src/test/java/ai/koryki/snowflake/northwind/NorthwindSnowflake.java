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
package ai.koryki.snowflake.northwind;

import ai.koryki.jdbc.ResultProcessor;
import ai.koryki.snowflake.SnowflakeDatabase;

import java.sql.Connection;
import java.time.ZoneId;

public class NorthwindSnowflake<C extends ResultProcessor<?>> extends SnowflakeDatabase<C> {

    public NorthwindSnowflake() throws Exception {
        super("northwind", connection());
    }

    /** Connect in the given model zone (default UTC). */
    public NorthwindSnowflake(ZoneId modelZone) throws Exception {
        super("northwind", connection(), modelZone);
    }

    public static Connection connection() throws Exception {

        return connection(
                System.getProperty("snowflake.northwind.user"),
                System.getProperty("snowflake.northwind.url") );
    }

}
