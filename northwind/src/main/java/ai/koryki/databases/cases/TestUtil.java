package ai.koryki.databases.cases;

import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.kql.Engine;
import ai.koryki.catalog.Util;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class TestUtil {

    public static Path expected(Path source, Path root, Path expected) {
        Path rela = root.relativize(source);
        return expected.resolve(rela) ;
    }
    public static Path expected(Path source, Path root, Path expected, String suffix) {
        Path rela = root.relativize(source);
        String s = source.toFile().toString();
        s = s.substring(s.lastIndexOf('.'));
        return Path.of(expected.resolve(rela).toFile().toString().replace(s, suffix)) ;

       // File oraSql = new File(s.replace(suffix, ".sql"));
    }

    public static <I extends ColumnInfo> void test(Path kql, String suffix, Engine<I, ListWithSqlResult<I>> engine, Path core, Path expCsv, Path expSql) throws IOException {
        test(kql, suffix, engine, core, expCsv, expSql, null);
    }


    public static <I extends ColumnInfo> void test(Path kql, String suffix, Engine<I, ListWithSqlResult<I>> engine, Path core, Path expCsv, Path expSql, String db) throws IOException {
        test(kql, suffix, engine, core, expCsv, expSql, db, false);
    }

    public static <I extends ColumnInfo> void test(Path kql, String suffix, Engine<I, ListWithSqlResult<I>> engine, Path core, Path expCsv, Path expSql, String db, boolean checktype) throws IOException {

        Path sibling = expected(kql, core, expCsv, ".csv");
        File expectedFile = sibling.toFile();

        String query = Files.readString(kql);

        if (db != null && query.contains("// ignore=" + db)) {
            return;
        }

        CSVAssert<I> csv = new CSVAssert<>(engine, query, kql.toFile().getName());

        String result;
        try {
            result = csv.getCsv();
        } finally {
            String sql = csv.getSql();
            if (sql != null) {
                sql = sql.lines()
                        .filter(line -> !line.startsWith("-- ignore="))
                        .collect(Collectors.joining(System.lineSeparator()));

                File oraSql = expected(kql, core, expSql, ".sql").toFile();
                if (oraSql.canRead()) {
                    String expected = Files.readString(oraSql.toPath());
                    csv.check(sql, expected, ".sql");
                } else {
                    Util.text(sql, oraSql);
                }
            }
        }




        if (expectedFile.canRead()) {
            String expected = Files.readString(sibling);
            csv.check(result, expected, ".csv");
        } else {
            Util.text(result, expectedFile);
        }

        if (checktype) {

            List<I> infos = csv.getResult().getInfos();

            ObjectMapper mapper = new ObjectMapper();
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(
                        expected(kql, core, expCsv, ".json").toFile(), infos);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
