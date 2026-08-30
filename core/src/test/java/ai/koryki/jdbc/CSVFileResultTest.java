package ai.koryki.jdbc;

import ai.koryki.antlr.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The file-writing side of {@link CSVFileResult} had no test until now — and a bug: {@code append}
 * wrote with {@code println} although {@code toCSV} already appends the line break. Every data row
 * was thereby followed by a blank line, and the second break was the platform's, so one file
 * contained both forms.
 */
class CSVFileResultTest {

    @Test
    void writesExactlyOneLinePerRecord(@TempDir Path dir) throws Exception {

        File out = dir.resolve("rows.csv").toFile();

        CSVFileResult<ColumnInfo> result = new CSVFileResult<>(out);
        result.append(List.of("a", "b"));
        result.append(List.of("c", "d"));
        result.close();

        String text = Files.readString(out.toPath());

        // No blank-line gap, and the break comes from toCSV alone.
        assertEquals("\"a\", \"b\"" + Text.NL + "\"c\", \"d\"" + Text.NL, text);
        assertEquals(2, text.split(Text.NL).length, "expected exactly two lines");
        assertEquals(-1, text.indexOf("\r"), "no platform-dependent line break in the file");
    }
}
