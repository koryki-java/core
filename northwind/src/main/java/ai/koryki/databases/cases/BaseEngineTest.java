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

    /** The schema whose fixtures this suite runs: {@code northwind}, {@code typecheck}, … */
    protected abstract String schema();

    protected String suffix() {
        return ".kql";
    }

    /**
     * The shared queries from the fixture project — the same for every dialect.
     *
     * <p>They used to hang in the module through a {@code privatetest} symlink. The path now comes
     * from {@link Fixtures}, which derives it from {@code -Dtest.root}.
     */
    protected Path queriesRoot() {
        return Fixtures.queries(schema());
    }

    /** The expected result — shared, because the same query must return the same rows everywhere. */
    protected Path expectedCsv() {
        return Fixtures.expectedCsv(schema());
    }

    /** The expected SQL — different per dialect. */
    protected Path expectedSql() {
        return Fixtures.expectedSql(db, schema());
    }

    /**
     * The violation goldens.
     *
     * <p>A fixture this dialect cannot express produces neither SQL nor CSV; without a golden the
     * skip would be invisible, and a skip that appears or disappears would look like a passing run.
     * The golden records what the dialect said.
     */
    protected Path expectedViolations() {
        return Fixtures.expectedViolations(db, schema());
    }

    /**
     * Extra fixtures only this module has, or {@code null}.
     *
     * <p>While the shared fixtures hung in the module by symlink, a single walk covered both.
     * Without the symlink two roots are needed — only the {@code parse/} fixtures of some dialects
     * are affected, whose expectations live in the module as well.
     */
    protected Path localQueries() {
        return null;
    }

    protected Path localExpectedCsv() {
        return null;
    }

    protected Path localExpectedSql() {
        return null;
    }

    protected Path localExpectedViolations() {
        return localExpectedSql() == null ? null
                : localExpectedSql().resolveSibling("violations");
    }

    public BaseEngineTest(String db) {
        this(db, false);
    }

    public BaseEngineTest(String db, boolean checktype) {
        this.db = db;
        this.checktype = checktype;
    }

    protected Stream<Path> testFiles() throws IOException {
        Stream<Path> shared = walk(queriesRoot());
        return localQueries() == null ? shared : Stream.concat(shared, walk(localQueries()));
    }

    private Stream<Path> walk(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return Stream.empty();   // schema without module-owned fixtures
        }
        return Files.walk(root, FileVisitOption.FOLLOW_LINKS)
                .filter(p -> p.toString().endsWith(suffix()))
                // 'invalid' queries assert a validation/parse failure (see the transpiler
                // tests); they cannot execute, so the engine tests skip them.
                .filter(p -> !p.getFileName().toString().startsWith("invalid"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testFiles")
    void testEachFile(Path kql) throws IOException {

        // Which expectations apply is decided by the root the fixture was found under.
        boolean local = localQueries() != null && kql.startsWith(localQueries());

        TestUtil.test(kql, suffix(), engine,
                local ? localQueries() : queriesRoot(),
                local ? localExpectedCsv() : expectedCsv(),
                local ? localExpectedSql() : expectedSql(),
                local ? localExpectedViolations() : expectedViolations(),
                db, checktype);
    }

    public String getDb() {
        return db;
    }

    public void setDb(String db) {
        this.db = db;
    }
}
