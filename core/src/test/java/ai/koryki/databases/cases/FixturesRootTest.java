package ai.koryki.databases.cases;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code test.root} is mandatory — which makes the error message the entire user interface. Both
 * failure cases are checked here and not across the Gradle layers: there, project and user
 * settings provide the same value, so a probe would say nothing. (That is exactly what my first
 * attempt failed on: the suite ran green although the setting had been removed from the project —
 * it also stood in ~/.gradle/gradle.properties.)
 */
class FixturesRootTest {

    @Test
    void aMissingSettingNamesThePropertyAndAnExample() {

        for (String value : new String[]{null, "", "   "}) {
            String msg = assertThrows(IllegalStateException.class, () -> Fixtures.resolve(value)).getMessage();
            assertTrue(msg.contains(Fixtures.ROOT_PROPERTY), "does not name the property: " + msg);
            // Checked as a concrete invocation, not by the directory name: that name changed three
            // times in one day, and pinning it here only means the test moves with every rename
            // without saying anything more.
            assertTrue(msg.contains("-D" + Fixtures.ROOT_PROPERTY + "="), "gives no example: " + msg);
        }
    }

    @Test
    void aWrongPathIsReportedAsSuch() {

        String msg = assertThrows(IllegalStateException.class,
                () -> Fixtures.resolve("/path/that/does/not/exist")).getMessage();

        // The path has to appear in the message — otherwise a typo would show up as
        // "the fixtures have vanished".
        assertTrue(msg.contains("/path/that/does/not/exist"), "does not name the path: " + msg);
        assertTrue(msg.contains("is not a directory"), "does not say what is missing: " + msg);
    }
}
