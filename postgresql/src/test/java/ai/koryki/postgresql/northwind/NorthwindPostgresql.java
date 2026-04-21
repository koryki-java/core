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
package ai.koryki.postgresql.northwind;

import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.jdbc.JdbcDatabase;
import ai.koryki.jdbc.ResultProcessor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class NorthwindPostgresql<P extends ResultProcessor<?>> extends JdbcDatabase<P> {

    public NorthwindPostgresql() throws SQLException {
        this("northwind", connection());
    }

    public NorthwindPostgresql(String name) throws SQLException {
        this(name, connection());
    }

    public NorthwindPostgresql(String name, String user, String passwort, String url) throws SQLException {
        this(name, connection(user, passwort, url));
    }

    public NorthwindPostgresql(String name, Connection conn) {
        super(name, conn);
    }

    public static Connection connection() throws SQLException {
        return connection(System.getProperty("postgresql.northwind.user"),
            System.getProperty("postgresql.northwind.password"),
            System.getProperty("postgresql.northwind.url") );
    }

    public static Connection connection(String user, String passwort, String url) throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", passwort);

        return DriverManager.getConnection(url, props);
    }
}
