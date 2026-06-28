package ai.koryki.kql;

import ai.koryki.databases.temporal.duckdb.TemporalService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Zone-aware literal reconciliation (docs/TEMPORAL.md): an {@code EPOCH:<unit>} column compared to a
 * timestamp literal renders the literal as an epoch count, and that count is taken by interpreting the
 * literal in the renderer's <em>model zone</em> — not a hardcoded UTC. The same KQL therefore transpiles
 * to a different integer under a different model zone, which is exactly what keeps a bare (index-friendly)
 * EPOCH column comparable across storage zones.
 */
public class EpochLiteralZoneTest {

    private static final String KQL =
            "FIND check_temporal c FILTER c.timestamp_unix_epoche > \"2024-06-01 12:00:00\" FETCH c.nr";

    private static final LocalDateTime LITERAL = LocalDateTime.of(2024, 6, 1, 12, 0, 0);

    private static LinkResolver resolver;

    @BeforeAll
    static void setUp() throws IOException {
        resolver = TemporalService.resolver();
    }

    private static String sql(ZoneId modelZone) throws IOException {
        KQLTranspiler transpiler = KQLTranspiler
                .builder(new ByteArrayInputStream(KQL.getBytes(StandardCharsets.UTF_8)), resolver).build();
        return transpiler.getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, modelZone));
    }

    @Test
    void epochLiteralIsInterpretedInTheModelZone() throws IOException {
        long utc = LITERAL.atZone(ZoneId.of("UTC")).toEpochSecond();
        long ny  = LITERAL.atZone(ZoneId.of("America/New_York")).toEpochSecond();   // EDT (-04:00) in June
        assertNotEquals(utc, ny, "the offset must make the two epoch counts differ");

        assertTrue(sql(ZoneId.of("UTC")).contains(Long.toString(utc)),
                "UTC model zone must render the literal as its UTC epoch-seconds");
        assertTrue(sql(ZoneId.of("America/New_York")).contains(Long.toString(ny)),
                "a New York model zone must render the literal as its New-York epoch-seconds");
    }
}
