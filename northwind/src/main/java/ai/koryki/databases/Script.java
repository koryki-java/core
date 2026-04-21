package ai.koryki.databases;

import ai.koryki.databases.northwind.duckdb.BuildNorthwind;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Script {

    public static void executeScript(Connection c, String script) throws IOException, SQLException {
        List<String> stmts = statements(script);

        try (Statement stmt = c.createStatement()) {

            for (String s : stmts) {
                s = s.trim();
                if (!s.isEmpty() && !s.startsWith("--")) {
                    try {
                        stmt.execute(s.trim());
                    } catch (SQLException e) {
                        throw new RuntimeException(s, e);
                    }
                }
            }
        }
    }

    public static List<String> statements(String resource) throws IOException {
        String sql;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                BuildNorthwind.class.getResourceAsStream(resource), StandardCharsets.UTF_8))) {

            sql = reader.lines().filter(l -> !l.trim().startsWith("--")).collect(Collectors.joining("\n"));

            return Arrays.asList(sql.split(";"));
        }
    }

}
