package ai.koryki.databases.cases;

import ai.koryki.kql.Engine;
import ai.koryki.databases.FileAsserter;
import ai.koryki.catalog.Util;
import ai.koryki.jdbc.ColumnInfo;

import java.io.File;
import java.util.List;
import java.util.function.Supplier;


public class CSVAssert<I extends ColumnInfo> implements TestCase {

    private String kql;
    private String sql;
    private String expected;
    private Engine<I, ListWithSqlResult<I>> engine;
    private String name;
    private ListWithSqlResult<I> result;

    public CSVAssert(Engine<I, ListWithSqlResult<I>> engine, String kql, String expected, String name) {
        this.engine = engine;
        this.kql = kql;
        this.expected = expected;
        this.name = name;
        run();
    }

    public CSVAssert(Engine<I, ListWithSqlResult<I>> engine, String kql, String name) {
        this.engine = engine;
        this.kql = kql;
        this.name = name;
    }


    @Override
    public void run()  {

        String csv = getCsv();
        check(csv, expected, "");
    }

    public void check(String csv, String expected, String suffix) {
        if (name != null) {
            Util.text(csv, new File("build/" + name + suffix));
        }
        FileAsserter.scriptAssert(expected, csv, "");
    }

    public String getCsv() {
        Supplier<ListWithSqlResult<I>> processor = ListWithSqlResult::new;

        sql =  engine.toSql(kql);
        // formatting comes from the engine's configured Format (engine.setFormat),
        // not from the processor — don't force one here.
        result = engine.executeKQL(kql, processor);

        //sql = result.getSql();

        if (name.endsWith("stable")) {
            return result.toCSV();
        }
        String csv = result.toSortedCSV();
        return csv;
    }

    public String getSql() {
        return sql;
    }

    public ListWithSqlResult<I> getResult() {
        return result;
    }
}
