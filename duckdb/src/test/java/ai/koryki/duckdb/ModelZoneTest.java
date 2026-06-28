package ai.koryki.duckdb;

import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.databases.northwind.duckdb.NorthwindDuckdb;
import ai.koryki.databases.temporal.duckdb.TemporalService;
import ai.koryki.kql.Engine;
import ai.koryki.kql.HeaderInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.ZoneId;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The model zone is a single source of truth carried by {@link ai.koryki.jdbc.JdbcDatabase}: it pins
 * the session, drives naive-instant interpretation AND the display of instants at the read boundary.
 * The default is UTC; with a non-UTC model zone the same absolute instant
 * ({@code timestamp_zoned} nr 1 = {@code 2024-04-12 12:14:40Z}) reads back in that zone's wall-clock.
 */
public class ModelZoneTest {

    private static final String QUERY = "FIND check_temporal c FILTER c.nr = 1 FETCH c.timestamp_zoned";

    private static String readInstant(ZoneId modelZone) throws IOException {
        Engine<HeaderInfo, ListWithSqlResult<HeaderInfo>> engine =
                Engine.builder(NorthwindDuckdb.<ListWithSqlResult<HeaderInfo>>northwind(modelZone),
                                TemporalService.resolver(), new SqlQueryRenderer(modelZone))
                        .format(new StableFormat(Locale.ROOT)).build();
        return engine.executeKQL(QUERY, ListWithSqlResult::new).toSortedCSV();
    }

    @Test
    void instantDisplaysInTheModelZone() throws IOException {
        // default UTC: the canonical wall-clock
        assertTrue(readInstant(ZoneId.of("UTC")).contains("2024-04-12 12:14:40"),
                "UTC model zone must show the UTC wall-clock");
        // 2024-04-12 12:14:40Z is 08:14:40 in America/New_York (EDT, -04:00 in April)
        assertTrue(readInstant(ZoneId.of("America/New_York")).contains("2024-04-12 08:14:40"),
                "a non-UTC model zone must shift the displayed instant accordingly");
    }
}
