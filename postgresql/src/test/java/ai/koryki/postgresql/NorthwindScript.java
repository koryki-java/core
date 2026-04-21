package ai.koryki.postgresql;

import java.io.File;
import java.io.IOException;

public class NorthwindScript {

    public static void main(String[] args) throws IOException, InterruptedException {

        //psql -v ON_ERROR_STOP=1 -U PG -h johannes-x600 -d northwind -f /home/johannes/IdeaProjects/korykiai/postgresql/test.sql

        File root = new File(".");
        System.out.println(root.getAbsolutePath());

        File drop = new File("postgresql/src/test/resources/ai/koryki/databases/northwind/postgresql/drop.sql");
        if (drop.canRead()) {
            new Psql().runScript(drop, true);
        }

        File tables = new File("postgresql/src/test/resources/ai/koryki/databases/northwind/postgresql/tables.sql");
        if (tables.canRead()) {
            new Psql().runScript(tables, true);
        }

        File data = new File("postgresql/src/test/resources/ai/koryki/databases/northwind/postgresql/data.sql");
        if (data.canRead()) {
            new Psql().runScript(data, true);
        }

        File constraints = new File("postgresql/src/test/resources/ai/koryki/databases/northwind/postgresql/constraints.sql");
        if (constraints.canRead()) {
            new Psql().runScript(constraints, true);
        }

    }
}
