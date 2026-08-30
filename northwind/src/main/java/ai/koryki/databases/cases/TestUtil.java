package ai.koryki.databases.cases;

import ai.koryki.antlr.Text;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.databases.FileAsserter;
import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.kql.Engine;
import ai.koryki.iql.validate.ValidateException;
import tools.jackson.databind.ObjectMapper;

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


    /**
     * Whether a fixture failed only because this dialect declares one of its functions unsupported
     * — in which case it is skipped, exactly as a hand-written {@code // ignore=<dialect>} marker
     * would have done.
     *
     * <p>This replaces 18 such markers in the shared corpus, which restated what the dialect had
     * already declared: the page then printed both "Unsupported: sqlite" and "Results differ on
     * sqlite" for one fact, and the markers drifted twice (two on {@code starts_with} outlived the
     * overrides that made them unnecessary). Deriving the skip from the catalog keeps the two in
     * step, and leaves a surviving {@code ignore=} marker meaning exactly one thing: same SQL,
     * different answer.
     *
     * <p>Deliberately narrow — only when <em>every</em> error is an unsupported-function violation.
     * A query that is unsupported <em>and</em> wrong still fails.
     */
    public static boolean unsupportedOnThisDialect(Throwable t) {
        for (Throwable e = t; e != null; e = e.getCause()) {
            if (e instanceof ValidateException v && v.isOnlyUnsupported()) {
                return true;
            }
            if (e instanceof UnsupportedOperationException
                    && String.valueOf(e.getMessage()).contains("is not supported by this dialect")) {
                return true;
            }
            if (e.getCause() == e) {
                break;
            }
        }
        return false;
    }

    /**
     * Whether a fixture failed only because this dialect's schema does not have a column it reads
     * — in which case it is skipped, exactly as {@link #unsupportedOnThisDialect} does for a
     * function the catalog declares unsupported.
     *
     * <p>This replaces 41 hand-written {@code ignore=<dialect>} markers. They were the wrong tool
     * twice over: the marker's contract says "same SQL, different answer", and these fixtures
     * produce no SQL at all on the dialects in question; and nothing tied the marker to the reason,
     * so a column added later would have left it standing. The schemas differ on purpose —
     * {@code interval_year_month} exists only where the engine has an interval column type, which
     * Snowflake for one does not — so the difference belongs in the catalog, where it already is.
     *
     * <p>As narrow as its sibling: only when <em>every</em> error is an unknown column. A query that
     * reads a missing column <em>and</em> is wrong still fails.
     */
    public static boolean columnMissingOnThisDialect(Throwable t) {
        for (Throwable e = t; e != null; e = e.getCause()) {
            if (e instanceof ValidateException v && v.isOnlyUnknownColumn()) {
                return true;
            }
            if (e.getCause() == e) {
                break;
            }
        }
        return false;
    }

    /**
     * Whether this dialect cannot run the fixture at all, for a reason the catalog or the schema
     * already declares — an unsupported function or construct, a column this schema does not have,
     * or any mixture of the two. This is the predicate the harness uses.
     *
     * <p>Asking {@link #unsupportedOnThisDialect} and {@link #columnMissingOnThisDialect} separately
     * is not equivalent and was a bug: {@code duration_interval_year_month_ds_literal} reads a column
     * MariaDB does not declare <em>and</em> uses a duration MariaDB has no type for, so each narrow
     * predicate answered no and the fixture failed instead of skipping.
     */
    public static boolean cannotRunOnThisDialect(Throwable t) {
        for (Throwable e = t; e != null; e = e.getCause()) {
            if (e instanceof ValidateException v && v.isOnlyDialectLimitation()) {
                return true;
            }
            if (e instanceof UnsupportedOperationException
                    && String.valueOf(e.getMessage()).contains("is not supported by this dialect")) {
                return true;
            }
            if (e.getCause() == e) {
                break;
            }
        }
        return false;
    }

    /**
     * Records <em>what</em> the dialect rejected. Without this golden a skip would be invisible:
     * nothing would say that this dialect skips this fixture, and a skip that appears or disappears
     * would look like a passing run.
     *
     * <p>Generated, not hand-written — exactly like the SQL and CSV goldens. A hand-written
     * expectation would repeat what the code already says and then drift away from it; that
     * happened twice with the earlier {@code ignore=} markers.
     */
    private static void checkViolations(Path kql, Path core, Path expViolations, Throwable cause) throws IOException {

        if (expViolations == null) {
            return;
        }

        File golden = expected(kql, core, expViolations, ".txt").toFile();
        String actual = violationText(cause);

        if (golden.canRead()) {
            FileAsserter.scriptAssert(Files.readString(golden.toPath()), actual);
        } else {
            Fixtures.writeOrFail(actual, golden);
        }
    }

    /** The wording of the rejection: the violations themselves, otherwise the exception's message. */
    private static String violationText(Throwable t) {
        for (Throwable e = t; e != null; e = e.getCause()) {
            if (e instanceof ValidateException v) {
                return v.getMessage();
            }
            if (e instanceof UnsupportedOperationException) {
                return e.getMessage();
            }
            if (e.getCause() == e) {
                break;
            }
        }
        return String.valueOf(t.getMessage());
    }

    public static <I extends ColumnInfo> void test(Path kql, String suffix, Engine<I, ListWithSqlResult<I>> engine, Path core, Path expCsv, Path expSql) throws IOException {
        test(kql, suffix, engine, core, expCsv, expSql, null);
    }


    public static <I extends ColumnInfo> void test(Path kql, String suffix, Engine<I, ListWithSqlResult<I>> engine, Path core, Path expCsv, Path expSql, String db) throws IOException {
        test(kql, suffix, engine, core, expCsv, expSql, db, false);
    }

    public static <I extends ColumnInfo> void test(Path kql, String suffix, Engine<I, ListWithSqlResult<I>> engine, Path core, Path expCsv, Path expSql, String db, boolean checktype) throws IOException {
        test(kql, suffix, engine, core, expCsv, expSql, null, db, checktype);
    }

    /**
     * @param expViolations where the violation golden lives; null disables the check (callers that
     *                      predate it). See {@code BaseEngineTest#expectedViolations()} for why it
     *                      exists.
     */
    /** Compares the rendered SQL against this dialect's golden, or writes it when absent. */
    private static <I extends ColumnInfo> void checkSqlGolden(String sql, Path kql, Path core,
            Path expSql, CSVAssert<I> csv) throws IOException {
        if (sql == null) {
            return;
        }
        String cleaned = sql.lines()
                .filter(line -> !line.startsWith("-- ignore="))
                .collect(Collectors.joining(Text.NL));

        File oraSql = expected(kql, core, expSql, ".sql").toFile();
        if (oraSql.canRead()) {
            csv.check(cleaned, Files.readString(oraSql.toPath()), ".sql");
        } else {
            Fixtures.writeOrFail(cleaned, oraSql);
        }
    }

    public static <I extends ColumnInfo> void test(Path kql, String suffix, Engine<I, ListWithSqlResult<I>> engine, Path core, Path expCsv, Path expSql, Path expViolations, String db, boolean checktype) throws IOException {

        Path sibling = expected(kql, core, expCsv, ".csv");
        File expectedFile = sibling.toFile();

        String query = Files.readString(kql);

        if (db != null && query.contains("// ignore=" + db)) {
            // The marker means one thing: same SQL, different answer. So the answer is skipped —
            // but the SQL is not, because the marker asserts it. Returning here skipped both, and
            // that is how random()'s golden could claim `random() AS r` on MariaDB, Oracle and SQL
            // Server: none of the three has such a function, all three reject the statement, and
            // the fixture was ignored on all eight dialects so nothing ever rendered it either.
            // Rendering does not execute, so this stays free of the result the marker excludes.
            CSVAssert<I> render = new CSVAssert<>(engine, query, kql.toFile().getName());
            String rendered;
            try {
                rendered = render.renderSql();
            } catch (RuntimeException e) {
                // A marker claims "same SQL, different answer". If rendering throws there is no SQL,
                // so the claim is false and the fixture needs a different remedy -- that has to be
                // visible. This used to `return` instead, which checked nothing and recorded
                // nothing: the fixture simply vanished from the run on that dialect.
                //
                // The two causes the old comment named are both gone. Reading a column the dialect
                // does not declare is now derived from the catalog (cannotRunOnThisDialect), and a
                // construct an engine cannot express is declared rather than thrown at render time
                // (unsupported, SqlDialect.intervalSupport). Measured on all eight before closing
                // the hole: no marked fixture reaches this branch.
                throw new AssertionError("marker on " + kql.getFileName() + " claims 'same SQL, "
                        + "different answer', but rendering fails on " + db
                        + " -- the marker is the wrong remedy here", e);
            }
            checkSqlGolden(rendered, kql, core, expSql, render);
            return;
        }

        CSVAssert<I> csv = new CSVAssert<>(engine, query, kql.toFile().getName());

        String result;
        boolean unsupported = false;
        try {
            result = csv.getCsv();
        } catch (RuntimeException e) {
            if (!cannotRunOnThisDialect(e)) {
                throw e;
            }
            unsupported = true;   // the dialect cannot express this query; skip it
            result = null;
            checkViolations(kql, core, expViolations, e);
        } finally {
            if (!unsupported) {
                checkSqlGolden(csv.getSql(), kql, core, expSql, csv);
            }
        }




        if (unsupported) {
            return;
        }

        // The other direction: the query runs on this dialect. A leftover golden would mean it has
        // learned the construct since — that should stand out, not vanish.
        if (expViolations != null) {
            File stale = expected(kql, core, expViolations, ".txt").toFile();
            if (stale.canRead()) {
                throw new AssertionError("violation golden present, but the query runs here: " + stale
                        + Text.NL
                        + "The dialect handles the construct now — delete the golden.");
            }
        }

        if (expectedFile.canRead()) {
            String expected = Files.readString(sibling);
            csv.check(result, expected, ".csv");
        } else {
            Fixtures.writeOrFail(result, expectedFile);
        }

        if (checktype) {
            checkTypeGolden(csv, kql, core, expCsv);
        }
    }

    /**
     * Compares the <em>types</em> of the output columns against a golden — the physical type each
     * engine reports next to the family and encoding koryki declared for it.
     *
     * <p>This block used to call {@code writeValue} unconditionally: it overwrote the golden on
     * every run and therefore could not fail, whatever the engines answered. That is exactly what
     * {@code Fixtures.writeOrFail}'s own message warns about — a golden written every time only
     * confirms the current behaviour. Compounding it, no caller ever passed {@code checktype}, so
     * the block never ran at all and its 51 goldens sat unread.
     *
     * <p>It now behaves like the CSV and SQL goldens: present means compare, absent means write
     * once. Because the file lives under the shared {@code kql/} root, the first dialect to run
     * writes it and the other seven are held against it — which is what makes a type difference
     * between engines visible instead of merely recorded.
     */
    /**
     * The part of an output column's type that every engine must agree on: its family and its
     * encoding — koryki's promise about the column — plus the header it answers under.
     *
     * <p>Deliberately <em>only</em> the family. The rest of the descriptor describes how this
     * dialect happens to store the value and legitimately differs:
     *
     * <ul>
     *   <li>{@code physicalTypeName}/{@code precision}/{@code scale} — the engine's own name for
     *       its type: {@code INTEGER} on DuckDB, {@code INT} on MariaDB and SQL Server,
     *       {@code DECIMAL(5,0)} on PostgreSQL.</li>
     *   <li>{@code typeEncoding} — the storage shape the schema declares. A boolean is
     *       {@code NATIVE} where the engine has one and {@code BOOLEAN_FROM_INT} on Trino, Oracle
     *       and SQLite, which do not; intervals and times split the same way. That is what the
     *       {@code bool_encodings} fixture exists to exercise, so it cannot also be a shared claim.</li>
     * </ul>
     *
     * The family is what koryki promises regardless of storage — a TIME column answers as a TIME
     * everywhere — and a shared golden can only assert what must be the same everywhere.
     */
    private static <I extends ColumnInfo> String portableTypes(List<I> infos) {
        StringBuilder b = new StringBuilder();
        for (I info : infos) {
            TypeDescriptor t = info.getTypeDescriptor();
            b.append(info.getHeader()).append(" : ")
                    .append(t == null ? "unknown" : t.getTypeFamily())
                    .append(Text.NL);
        }
        return b.toString();
    }

    private static <I extends ColumnInfo> void checkTypeGolden(CSVAssert<I> csv, Path kql, Path core,
            Path expCsv) throws IOException {
        String actual = portableTypes(csv.getResult().getInfos());
        File golden = expected(kql, core, expCsv, ".json").toFile();
        if (golden.canRead()) {
            csv.check(actual, Files.readString(golden.toPath()), ".json");
        } else {
            Fixtures.writeOrFail(actual, golden);
        }
    }

}
