package ai.koryki.databases.cases;

import ai.koryki.antlr.AbstractReader;
import ai.koryki.kql.Engine;
import ai.koryki.databases.FileAsserter;
import ai.koryki.scaffold.Util;
import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.jdbc.ListResult;

import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;


public class CSVAssert implements TestCase {

    private String kql;
    private String expected;
    private Engine<ColumnInfo, ListResult<ColumnInfo>> engine;
    private String name;


    public CSVAssert(Engine<ColumnInfo, ListResult<ColumnInfo>> engine, String kql, String expected, String name) {
        this.engine = engine;
        this.kql = kql;
        this.expected = expected;
        this.name = name;
        run();
    }

    public CSVAssert(Engine<ColumnInfo, ListResult<ColumnInfo>> engine, String kql, String name) {
        this.engine = engine;
        this.kql = kql;
        this.name = name;
    }


    @Override
    public void run()  {

        String csv = getCsv();
        check(csv, expected);
    }

    public void check(String csv, String expected) {
        if (name != null) {
            Util.text(csv, new File("build/" + name + ".csv"));
        }
        FileAsserter.scriptAssert(expected, csv, "");
    }

    public String getCsv() {
        Supplier<ListResult<ColumnInfo>> processor = ListResult::new;
        engine.setInfo((t) -> t.infos(StableFormatInfo::new));
        ListResult<ColumnInfo> result = engine.executeKQL(kql, processor);


        if (name.endsWith("stable")) {
            return result.toCSV();
        }
        String csv = result.toSortedCSV();
        return csv;
    }

}
