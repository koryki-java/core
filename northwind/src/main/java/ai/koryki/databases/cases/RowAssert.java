package ai.koryki.databases.cases;

import ai.koryki.kql.Engine;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.scaffold.Util;
import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.jdbc.ListResult;

import java.io.File;
import java.sql.SQLException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RowAssert implements TestCase {
    private int rows;
    private String kql;
    private Engine<ColumnInfo, ListResult<ColumnInfo>> engine;
    private String name;


    public RowAssert(Engine<ColumnInfo, ListResult<ColumnInfo>> engine, String kql, int rows) throws SQLException {
        this(engine, kql, rows, null);
    }

    public RowAssert(Engine<ColumnInfo, ListResult<ColumnInfo>> engine, String kql, int rows, String name) throws SQLException {
        this.engine = engine;
        this.kql = kql;
        this.rows = rows;
        this.name = name;

        run();
    }

    @Override
    public void run() throws SQLException {
        Supplier<ListResult<ColumnInfo>> processor = () -> build();

        engine.setInfo((t) -> t.infos(HeaderInfo::new));

        ListResult<ColumnInfo> result = engine.executeKQL(kql, processor);
        if (name != null) {
            Util.text(result.toCSV(), new File("build/" + name + ".csv"));
        }
        assertEquals(rows, result.getRows().size());

    }

    private ListResult<ColumnInfo> build() {



        //List<ColumnInfo> infos = transpiler.infos(HeaderInfo::new);
        ListResult<ColumnInfo> result = new ListResult<ColumnInfo>();
        if (name != null) {
          //  result.setInfos(infos);
        }
        return result;
    }

}
