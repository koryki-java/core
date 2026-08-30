package ai.koryki.databases.cases;

import ai.koryki.kql.Engine;
import ai.koryki.databases.FileAsserter;
import ai.koryki.catalog.Util;
import ai.koryki.jdbc.ColumnInfo;

import java.io.File;
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
        if (".csv".equals(suffix) && numericallyEqual(expected, csv)) {
            return;
        }
        FileAsserter.scriptAssert(expected, csv, "");
    }

    /** Two values agree if they differ by less than this, relative to the larger one. */
    private static final java.math.BigDecimal TOLERANCE = new java.math.BigDecimal("1e-6");

    /**
     * Whether two result sets agree as <em>numbers</em> — cell by cell, with a relative tolerance —
     * rather than as text. Only for {@code .csv}: an SQL golden must match exactly.
     *
     * <p>The goldens are shared across eight engines, and engines genuinely disagree in the last
     * digits of a floating-point result. A sum over thousands of rows depends on the order the
     * engine added them in, because addition is not associative; a REAL column read back gives
     * {@code 9.649999} on one engine and {@code 9.65} on another. Measured across the corpus, that
     * noise stays below 1e-5 relative, while the differences worth catching — SQLite's
     * {@code to_decimal} losing four digits, a moving average off in the fourth — are well above it.
     *
     * <p>This replaces normalising the <em>text</em>, which could not do both jobs: the formatter
     * rounded every number to one decimal to stay stable, and thereby wrote {@code pi()} into the
     * goldens as {@code 3.1} and made 0.14 and 0.15 the same expected value. The goldens now carry
     * the value the engine returned; the tolerance lives where the comparison is.
     */
    private static boolean numericallyEqual(String expected, String actual) {
        String[] exp = expected.split("\\R");
        String[] act = actual.split("\\R");
        if (exp.length != act.length) {
            return false;
        }
        for (int i = 0; i < exp.length; i++) {
            if (exp[i].equals(act[i])) {
                continue;
            }
            String[] a = exp[i].split(", ");
            String[] b = act[i].split(", ");
            if (a.length != b.length) {
                return false;
            }
            for (int j = 0; j < a.length; j++) {
                if (!a[j].equals(b[j]) && !closeEnough(a[j], b[j])) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean closeEnough(String a, String b) {
        java.math.BigDecimal x = decimal(a);
        java.math.BigDecimal y = decimal(b);
        if (x == null || y == null) {
            return false;
        }
        java.math.BigDecimal scale = x.abs().max(y.abs());
        if (scale.signum() == 0) {
            return true;                                   // both zero, differing only in spelling
        }
        return x.subtract(y).abs()
                .divide(scale, java.math.MathContext.DECIMAL64)
                .compareTo(TOLERANCE) < 0;
    }

    /** The number inside a CSV cell ({@code "12.5"}), or null when the cell is not one. */
    private static java.math.BigDecimal decimal(String cell) {
        String s = cell.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        try {
            return new java.math.BigDecimal(s.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    public String getCsv() {
        Supplier<ListWithSqlResult<I>> processor = ListWithSqlResult::new;

        sql =  engine.toSql(kql);
        // formatting comes from the engine's configured Format (engine.setFormat),
        // not from the processor — don't force one here.
        result = engine.executeKQL(kql, processor);

        //sql = result.getSql();

        // Rows are compared text-sorted, so a fixture whose ORDER matters opts out by ending its
        // name in "stable". That never worked: `name` is the file name, so it ends in ".kql" and
        // the test could not be true — every fixture in the corpus was sorted, and no ORDER BY was
        // ever verified anywhere. Strip the extension so the opt-out is reachable.
        //
        // Sorting stays the default on purpose: most fixtures have no total order, and engines are
        // free to break ties differently. Only a query with a genuinely unique ordering key should
        // take the "stable" name — otherwise the fixture is flaky rather than strict.
        if (name.replaceFirst("\\.[^.]*$", "").endsWith("stable")) {
            return result.toCSV();
        }
        return result.toSortedCSV();
    }

    /**
     * Renders the SQL without executing it — for a fixture whose <em>result</em> is ignored on this
     * dialect but whose rendering must still be checked.
     */
    public String renderSql() {
        sql = engine.toSql(kql);
        return sql;
    }

    public String getSql() {
        return sql;
    }

    public ListWithSqlResult<I> getResult() {
        return result;
    }
}
