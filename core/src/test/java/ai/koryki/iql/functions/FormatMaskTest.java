package ai.koryki.iql.functions;

import org.junit.jupiter.api.Test;

import java.util.Map;

import ai.koryki.antlr.KorykiaiException;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FormatMaskTest {

    /**
     * The REAL vocabulary, not a rebuilt copy. A hand-maintained map used to stand here -- it was
     * missing HH12 and YY, and it still contained the name tokens after they had been removed. A
     * test that brings its own vocabulary checks the scanner but never the list that is translated
     * against later.
     */
    private static final Map<String, String> STRFTIME = FormatMask.STRFTIME;

    @Test
    public void translatesTokensLongestMatchFirst() {
        // HH24 must win over HH12 over HH, YYYY over YY
        assertEquals("'%d.%m.%Y %H:%M'", FormatMask.translate("'DD.MM.YYYY HH24:MI'", STRFTIME));
        assertEquals("'%H %I %I'", FormatMask.translate("'HH24 HH12 HH'", STRFTIME));
        assertEquals("'%Y %y'", FormatMask.translate("'YYYY YY'", STRFTIME));
    }

    /**
     * MONTH, MON, DAY and DY are not portable and are therefore rejected rather than translated:
     * measured, they gave five different answers for the same day, and none at all on SQLite. The
     * message names the way out instead of only refusing.
     */
    @Test
    public void nameTokensAreRejected() {
        for (String bad : FormatMask.REJECTED) {
            KorykiaiException e = assertThrows(KorykiaiException.class,
                    () -> FormatMask.translate("'YYYY " + bad + "'", STRFTIME));
            assertTrue(e.getMessage().contains(bad), e.getMessage());
            assertTrue(e.getMessage().contains("double quotes"), e.getMessage());
        }
        // inside quotes the word stays literal text and is not checked
        assertEquals("'%Y MONTH'", FormatMask.translate("'YYYY \"MONTH\"'", STRFTIME));
    }

    @Test
    public void quotedLiteralTextIsEmbeddedVerbatim() {
        // the naive replace-chain corrupted the literal DAY into %A
        assertEquals("'%Y DAY'", FormatMask.translate("'YYYY \"DAY\"'", STRFTIME));
    }

    @Test
    public void unknownTokensPassThrough() {
        assertEquals("'%Y-FF3'", FormatMask.translate("'YYYY-FF3'", STRFTIME));
    }

    @Test
    public void nonLiteralPassesThroughUntranslated() {
        assertEquals("c.format_col", FormatMask.translate("c.format_col", STRFTIME));
    }

    @Test
    public void pmTokenTranslates() {
        // the MariaDB replace-chain was missing PM entirely
        assertEquals("'%I:%M %p'", FormatMask.translate("'HH:MI PM'", STRFTIME));
    }

    // ---- the shared vocabularies the dialects actually use ----

    @Test
    public void sharedVocabulariesCoverTheSameTokens() {
        assertEquals(FormatMask.STRFTIME.keySet(), FormatMask.MYSQL.keySet());
        assertEquals(FormatMask.STRFTIME.keySet(), FormatMask.TOKENS);
    }

    @Test
    public void sharedVocabulariesTranslatePm() {
        // SQLite and Trino carried replace-chains that stopped at AM, leaving a literal PM
        assertEquals("'%I:%M %p'", FormatMask.translate("'HH12:MI PM'", FormatMask.STRFTIME));
        assertEquals("'%h:%i %p'", FormatMask.translate("'HH12:MI PM'", FormatMask.MYSQL));
    }

    @Test
    public void sharedVocabulariesProtectQuotedLiterals() {
        // the chains substituted inside "..." — MM below must survive as text
        assertEquals("'%Y MM'", FormatMask.translate("'YYYY \"MM\"'", FormatMask.STRFTIME));
        assertEquals("'%Y MM'", FormatMask.translate("'YYYY \"MM\"'", FormatMask.MYSQL));
    }

    @Test
    public void scanReportsTokensAndLiteralRuns() {
        // the path SQL Server compiles through: tokens become expressions, literals stay text
        StringBuilder trace = new StringBuilder();
        FormatMask.scan("DD.MM \"at\" HH24", FormatMask.TOKENS,
                token -> trace.append('<').append(token).append('>'),
                literal -> trace.append('[').append(literal).append(']'));
        assertEquals("<DD>[.]<MM>[ at ]<HH24>", trace.toString());
    }
}
