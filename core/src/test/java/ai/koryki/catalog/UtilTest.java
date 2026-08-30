package ai.koryki.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That {@code Util.text} publishes a file only when it is complete.
 *
 * <p>The occasion was real damage: the shared type goldens under {@code expected/kql/…} have
 * <em>one</em> path per fixture for all dialects, and the eight dialect modules run as separate
 * processes in parallel. When a golden is missing, all eight write it at once. On 2026-08-05 two
 * of 242 new goldens came out mutilated that way and were committed — they surfaced only when
 * Snowflake joined and suddenly made all eight dialects break the same two tests.
 *
 * <p>The test reproduces this with threads rather than processes. That is not the same
 * concurrency, but it hits the same window between truncating and filling, and it fails reliably
 * against the old version ({@code new FileOutputStream(out)}).
 */
class UtilTest {

    /** Two contents of different length — a torn write is then recognisable by its length. */
    private static final String SHORT_TEXT = "Id : TEXT\n";
    private static final String LONG_TEXT = "avg_price : FLOAT\nId : INTEGER\nnth : INTEGER\n";

    @Test
    void neverPublishesHalfAFile(@TempDir Path dir) throws Exception {
        File target = dir.resolve("golden.json").toFile();
        Set<String> complete = Set.of(SHORT_TEXT, LONG_TEXT);
        Util.text(SHORT_TEXT, target);          // so the reader never sees a file that is not there yet

        AtomicBoolean done = new AtomicBoolean();
        List<String> torn = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newCachedThreadPool();

        Future<?> reader = pool.submit(() -> {
            while (!done.get()) {
                try {
                    String seen = Files.readString(target.toPath());
                    if (!complete.contains(seen)) {
                        torn.add(seen);
                    }
                } catch (IOException e) {
                    // A read error may occur between two renames; an
                    // *incomplete* content may not.
                }
            }
        });

        List<Future<?>> writers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String content = i % 2 == 0 ? SHORT_TEXT : LONG_TEXT;
            writers.add(pool.submit(() -> {
                for (int round = 0; round < 100; round++) {
                    Util.text(content, target);
                }
            }));
        }
        for (Future<?> f : writers) {
            f.get();
        }
        done.set(true);
        reader.get();
        pool.shutdown();

        assertTrue(torn.isEmpty(),
                () -> "read incomplete: " + torn.stream().limit(3).toList());
        assertTrue(complete.contains(Files.readString(target.toPath())),
                "the final content is not one of the complete values");
    }

    @Test
    void leavesNoTemporaryFilesBehind(@TempDir Path dir) throws Exception {
        File target = dir.resolve("golden.json").toFile();
        Util.text(LONG_TEXT, target);
        Util.text(SHORT_TEXT, target);

        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(List.of(target.toPath()), files.toList(),
                    "nothing may be left beside the target");
        }
        assertEquals(SHORT_TEXT, Files.readString(target.toPath()));
    }
}
