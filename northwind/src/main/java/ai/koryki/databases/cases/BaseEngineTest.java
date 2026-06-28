package ai.koryki.databases.cases;

import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.kql.Engine;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseEngineTest<I extends ColumnInfo> {

    protected Engine<I, ListWithSqlResult<I>> engine;

    protected String db;
    protected boolean checktype;
    protected abstract String queriesRoot();
    protected abstract String expectedCsv();
    protected abstract String expectedSql();

    protected String suffix() {
        return ".kql";
    }

    public BaseEngineTest(String db) {
        this(db, false);
    }

    public BaseEngineTest(String db, boolean checktype) {
        this.db = db;
        this.checktype = checktype;
    }

    protected Stream<Path> testFiles() throws IOException {
        return Files.walk(Path.of(queriesRoot()), FileVisitOption.FOLLOW_LINKS)
                .filter(p -> p.toString().endsWith(suffix()))
                // 'invalid' queries assert a validation/parse failure (see the transpiler
                // tests); they cannot execute, so the engine tests skip them.
                .filter(p -> !p.getFileName().toString().startsWith("invalid"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testFiles")
    void testEachFile(Path kql) throws IOException {
        TestUtil.test(kql, suffix(), engine, Path.of(queriesRoot()), Path.of(expectedCsv()), Path.of(expectedSql()), db, checktype);
    }

    public String getDb() {
        return db;
    }

    public void setDb(String db) {
        this.db = db;
    }
}
