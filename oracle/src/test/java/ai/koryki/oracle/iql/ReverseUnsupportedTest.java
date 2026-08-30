package ai.koryki.oracle.iql;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.validate.Violation;
import ai.koryki.kql.KQLTranspiler;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Oracle's REVERSE reverses <em>bytes</em>, not characters. Measured against the live database,
 * {@code reverse('Königlich')} comes back as corrupted text where every other supported engine
 * returns {@code hcilginöK} — with no error and no warning.
 *
 * <p>A function that silently yields mojibake is worse than one that is absent, so Oracle declares
 * it unsupported. Pinned here precisely because the function <em>does</em> exist on Oracle and
 * <em>does</em> appear to work on ASCII: nothing about the engine would tell the next reader why
 * this is deliberate. Transpile-only, no database needed.
 */
public class ReverseUnsupportedTest {

    @Test
    public void reverseIsRefusedOnOracle() throws IOException {
        List<Violation> violations = KQLTranspiler.builder(new ByteArrayInputStream("""
                FIND customers c
                FETCH reverse(c.company_name) reversed
                """.getBytes(StandardCharsets.UTF_8)), NorthwindService.resolver())
                .functions(OracleDialect.INSTANCE.getFunctionRenderer())
                .build().violations();

        assertFalse(violations.isEmpty(), "reverse should be refused on Oracle");
        assertTrue(violations.stream().anyMatch(Violation::isUnsupported),
                "refusal must carry the UNSUPPORTED category, which is what the test harnesses "
                        + "skip on: " + violations);
    }

    /** The neighbouring case: a function Oracle renders fine is not caught by the same net. */
    @Test
    public void upperIsStillAccepted() throws IOException {
        assertTrue(KQLTranspiler.builder(new ByteArrayInputStream("""
                FIND customers c
                FETCH upper(c.company_name) shout
                """.getBytes(StandardCharsets.UTF_8)), NorthwindService.resolver())
                .functions(OracleDialect.INSTANCE.getFunctionRenderer())
                .build().violations().isEmpty());
    }
}
