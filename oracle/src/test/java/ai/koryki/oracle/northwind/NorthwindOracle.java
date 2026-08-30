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
package ai.koryki.oracle.northwind;

import ai.koryki.oracle.OracleDatabase;
import ai.koryki.jdbc.ResultProcessor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.Properties;

public class NorthwindOracle<P extends ResultProcessor<?>> extends OracleDatabase<P> {

    public NorthwindOracle() throws SQLException {
        this("northwind", connection());
    }

    /** Connect in the given model zone (default UTC). */
    public NorthwindOracle(ZoneId modelZone) throws SQLException {
        this("northwind", connection(), modelZone);
    }

    public NorthwindOracle(String name) throws SQLException {
        this(name, connection());
    }

    public NorthwindOracle(String name, String user, String passwort, String url) throws SQLException {
        this(name, connection(user, passwort, url));
    }

    public NorthwindOracle(String name, Connection conn) {
        super(name, conn);
    }

    public NorthwindOracle(String name, Connection conn, ZoneId modelZone) {
        super(name, conn, modelZone);
    }

    @Override
    public void execute(String sql, java.util.function.Consumer<java.sql.PreparedStatement> statementConsumer) {
        try {
            super.execute(sql, statementConsumer);
        } catch (RuntimeException e) {
            recoverConnection();
            throw e;
        }
    }

    private void recoverConnection() {
        try {
            getConnection().rollback();
        } catch (SQLException rollbackEx) {
            try {
                setConnection(connection());
            } catch (SQLException reconnectEx) {
                reconnectEx.addSuppressed(rollbackEx);
                throw new RuntimeException(reconnectEx);
            }
        }
    }

    public static Connection connection() throws SQLException {
        return connection(System.getProperty("oracle.northwind.user"),
            System.getProperty("oracle.northwind.password"),
            System.getProperty("oracle.northwind.url") );
    }

    public static Connection connection(String user, String password, String url) throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);

        return DriverManager.getConnection(url, props);
    }
}
