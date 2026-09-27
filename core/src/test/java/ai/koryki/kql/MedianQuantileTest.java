/*
 * Copyright 2025-2026 Johannes Zemlin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package ai.koryki.kql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.SqlQueryRenderer;
import java.io.IOException;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@code median}, {@code quantile_cont} and {@code quantile_disc} — the ordered-set aggregates.
 *
 * <p>The default template renders {@code MEDIAN(x)} and the SQL-standard {@code PERCENTILE_CONT(p)
 * WITHIN GROUP (ORDER BY x)} form, which is what DuckDB (tested here), PostgreSQL, Oracle and
 * Snowflake all accept unchanged — see {@code AggregateFunctions} for the measurement. PostgreSQL
 * is the one exception for {@code median} itself, tested in the postgresql module; MariaDB, SQLite,
 * Trino and SQL Server refuse all three, tested in their own modules, because {@code core} cannot
 * see those dialect classes (same split as {@code CountDistinctTest}).
 */
public class MedianQuantileTest {

    private static LinkResolver resolver;

    @BeforeAll
    static void setUp() throws IOException {
        resolver = NorthwindService.resolver();
    }

    @Test
    void medianRendersAsAPlainCall() {
        assertTrue(
                sql("FIND products p FETCH p.category_id, median(p.unit_price) m")
                        .contains("MEDIAN(p.unit_price)"));
    }

    @Test
    void medianDrivesGroupByInference() {
        assertTrue(
                sql("FIND products p FETCH p.category_id, median(p.unit_price) m")
                        .contains("GROUP BY"));
    }

    /** The fraction comes first in KQL, matching the SQL-standard {@code WITHIN GROUP} shape. */
    @Test
    void quantileContRendersAsPercentileContWithinGroup() {
        String sql = sql("FIND products p FETCH p.category_id, quantile_cont(0.5, p.unit_price) m");
        assertTrue(sql.contains("PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY p.unit_price)"), sql);
    }

    @Test
    void quantileDiscRendersAsPercentileDiscWithinGroup() {
        String sql =
                sql("FIND products p FETCH p.category_id, quantile_disc(0.25, p.unit_price) m");
        assertTrue(sql.contains("PERCENTILE_DISC(0.25) WITHIN GROUP (ORDER BY p.unit_price)"), sql);
    }

    /**
     * {@code quantile_disc} never interpolates, so koryki declares it to return the same type as
     * its *value* argument rather than the fractional type {@code median}/{@code quantile_cont}
     * carry — measured on DuckDB: {@code quantile_disc(0.5, x)} over integers answers an INTEGER,
     * {@code quantile_cont(0.5, x)} a DOUBLE.
     */
    @Test
    void quantileDiscKeepsTheValueArgumentsTypeUnlikeQuantileCont() {
        List<HeaderInfo> infos =
                analyze(
                        "FIND products p FETCH p.units_in_stock stock, "
                                + "quantile_disc(0.5, p.units_in_stock) qd, "
                                + "quantile_cont(0.5, p.units_in_stock) qc");
        var stockFamily = infos.get(0).getTypeDescriptor().getTypeFamily();
        var discFamily = infos.get(1).getTypeDescriptor().getTypeFamily();
        var contFamily = infos.get(2).getTypeDescriptor().getTypeFamily();
        assertTrue(
                discFamily.equals(stockFamily),
                "quantile_disc should carry units_in_stock's own family: " + infos);
        assertTrue(
                !contFamily.equals(stockFamily),
                "quantile_cont should NOT carry units_in_stock's own family, it interpolates: "
                        + infos);
    }

    private static List<HeaderInfo> analyze(String kql) {
        return new Generator<>(
                        resolver,
                        () -> new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")),
                        HeaderInfo::new)
                .analyze(kql);
    }

    private static String sql(String kql) {
        return sql(DuckdbBaseDialect.INSTANCE, kql);
    }

    private static String sql(SqlDialect dialect, String kql) {
        return KQLTranspiler.builder(kql, resolver)
                .functions(dialect.getFunctionRenderer())
                .build()
                .getSql(new SqlQueryRenderer(dialect, ZoneId.of("UTC")));
    }
}
