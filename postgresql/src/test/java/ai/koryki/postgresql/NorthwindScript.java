package ai.koryki.postgresql;

import ai.koryki.databases.Script;
import ai.koryki.postgresql.northwind.NorthwindPostgresql;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;

public class NorthwindScript {

    public static void main(String[] args) throws IOException, InterruptedException, SQLException {

        //psql -v ON_ERROR_STOP=1 -U PG -h johannes-x600 -d northwind -f /home/johannes/IdeaProjects/korykiai/postgresql/test.sql

        Connection connection = NorthwindPostgresql.connection();

        File drop = new File("postgresql/src/test/resources/ai/koryki/databases/northwind/postgresql/drop.sql");
        Script.executeScript(connection, Files.readString(drop.toPath()));

        File tables = new File("postgresql/src/test/resources/ai/koryki/databases/northwind/postgresql/tables.sql");
        Script.executeScript(connection, Files.readString(tables.toPath()));

        File data = new File("postgresql/src/test/resources/ai/koryki/databases/northwind/postgresql/data.sql");
        Script.executeScript(connection, Files.readString(data.toPath()));

        File constraints = new File("postgresql/src/test/resources/ai/koryki/databases/northwind/postgresql/constraints.sql");
        Script.executeScript(connection, Files.readString(constraints.toPath()));
    }
}
