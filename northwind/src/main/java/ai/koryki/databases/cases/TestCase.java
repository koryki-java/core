package ai.koryki.databases.cases;

import java.io.IOException;
import java.sql.SQLException;


public interface TestCase {

    public void run() throws SQLException, IOException;

}
