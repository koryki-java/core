package ai.koryki.duckdb.tool;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BuildNorthwind {

    public static void main(String[] args) throws SQLException, IOException {

        try (Connection c = DriverManager.getConnection("jdbc:duckdb:build/northwind.duckdb" )) {

            c.setAutoCommit(false);

            String tables = "/ai/koryki/databases/northwind/duckdb/tables.sql";
            //String constraints = "/ai/koryki/databases/northwind/duckdb/constraints.sql";
            String data = "/ai/koryki/databases/northwind/duckdb/data.sql";

            executeScript(tables, c);
            //executeScript(constraints, c);
            executeScript(data, c);
            c.commit();
        }
    }

    private static void executeScript(String tables, Connection c) throws IOException, SQLException {
        List<String> stmts = statements(tables);

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
