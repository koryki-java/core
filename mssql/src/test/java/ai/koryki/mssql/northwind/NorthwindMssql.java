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
package ai.koryki.mssql.northwind;

import ai.koryki.mssql.MssqlDatabase;
import ai.koryki.jdbc.ResultProcessor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.Properties;

public class NorthwindMssql<P extends ResultProcessor<?>> extends MssqlDatabase<P> {

    public NorthwindMssql(ZoneId modelZone) throws SQLException {
        this("northwind", connection(), modelZone);
    }
    public NorthwindMssql(String name, Connection conn, ZoneId modelZone) {
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
        return connection(System.getProperty("mssql.northwind.user"),
            System.getProperty("mssql.northwind.password"),
            System.getProperty("mssql.northwind.url") );
    }

    public static Connection connection(String user, String password, String url) throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);

        return DriverManager.getConnection(url, props);
    }
}
