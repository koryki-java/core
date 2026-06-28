package ai.koryki.tools.docs;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.databases.temporal.duckdb.TemporalService;
import ai.koryki.databases.typecheck.duckdb.TypecheckService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.mssql.iql.MssqlDialect;
import ai.koryki.oracle.iql.OracleDialect;
import ai.koryki.postgresql.iql.PostgreSqlDialect;
import ai.koryki.snowflake.iql.SnowflakeDialect;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Regenerates the per-dialect function/operator specification pages from the
 * catalog and the runnable KQL samples. This is a generator, not an assertion:
 * it overwrites {@code docs/spec/<dialect>.md} every run so the spec can never
 * drift from the catalog.
 */
class DialectSpecTest {

    private static final Path SAMPLES = Path.of("src/main/resources/ai/koryki/tools/docs/samples");

    /** Filesystem-safe sample slugs -> the operator's catalog key (its surface text). */
    private static final Map<String, String> OPERATOR_SLUGS = Map.ofEntries(
            Map.entry("eq", "="), Map.entry("lt", "<"), Map.entry("le", "<="),
            Map.entry("gt", ">"), Map.entry("ge", ">="), Map.entry("like", "LIKE"),
            Map.entry("between", "BETWEEN"), Map.entry("in", "IN"), Map.entry("isnull", "ISNULL"),
            Map.entry("and", "AND"), Map.entry("or", "OR"), Map.entry("not", "NOT"));

    @Test
    void generateDialectSpecs() throws IOException {
        Map<String, LinkResolver> resolvers = Map.of(
                "typecheck", TypecheckService.resolver(),
                "temporal", TemporalService.resolver(),
                "ai/koryki/databases/northwind", NorthwindService.resolver());

        List<DialectSpecGenerator.Sample> samples = loadSamples();
        DialectSpecGenerator generator = new DialectSpecGenerator(resolvers);

        Map<String, SqlDialect> dialects = new LinkedHashMap<>();
        dialects.put("duckdb", DuckdbBaseDialect.INSTANCE);
        dialects.put("oracle", OracleDialect.INSTANCE);
        dialects.put("snowflake", SnowflakeDialect.INSTANCE);
        dialects.put("mssql", MssqlDialect.INSTANCE);
        dialects.put("postgresql", PostgreSqlDialect.INSTANCE);

        for (Map.Entry<String, SqlDialect> e : dialects.entrySet()) {
            String md = generator.dialectSpec(e.getKey(), e.getValue(),
                    StandardFunctions.registry(), samples);
            Path out = Path.of("../docs/spec/" + e.getKey() + ".md");
            Files.createDirectories(out.getParent());
            Files.writeString(out, md);
        }
    }

    private static List<DialectSpecGenerator.Sample> loadSamples() throws IOException {
        if (!Files.isDirectory(SAMPLES)) {
            return List.of();
        }
        List<DialectSpecGenerator.Sample> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(SAMPLES, FileVisitOption.FOLLOW_LINKS)) {
            for (Path p : (Iterable<Path>) walk.filter(x -> x.toString().endsWith(".kql"))::iterator) {
                String db = SAMPLES.relativize(p).getName(0).toString();
                String file = p.getFileName().toString();
                String function = file.substring(0, file.length() - ".kql".length());
                int variant = function.indexOf("__");
                if (variant >= 0) {
                    function = function.substring(0, variant);
                }
                function = OPERATOR_SLUGS.getOrDefault(function, function);
                out.add(new DialectSpecGenerator.Sample(function, db, file, Files.readString(p)));
            }
        }
        return out;
    }
}
