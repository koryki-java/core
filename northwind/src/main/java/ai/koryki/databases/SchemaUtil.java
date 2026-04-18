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
package ai.koryki.databases;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SchemaUtil {

    public static final String INVALID = "invalid";


    public static boolean walk(Path source, Consumer<Path> consumer, List<Path> toSkip) throws IOException {
        List<Failure> fails = new ArrayList<>();


        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

                String name = file.toFile().getName();
                if (toSkip.contains(file) || name.startsWith(INVALID)) {
                    return FileVisitResult.CONTINUE;
                }

                try {
                    consumer.accept(file);
                } catch (RuntimeException e) {
                    Failure fail = new Failure(e, file.toString());
                    fails.add(fail);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                System.err.println("Fehler bei " + file + ": " + exc);
                return FileVisitResult.CONTINUE;
            }
        });

        if (!fails.isEmpty()) {
            System.out.println("failed files: " + fails.size());
            fails.forEach(f -> {System.out.println(f.getFile()); f.getThrowable().printStackTrace(System.out);});
        }

        return fails.isEmpty();
    }

    public static Path targetPath(Path file, Path source, Path target, String suffix) {
        Path rel = source.relativize(file);
        Path t = target.resolve(rel);

        String filename = t.getFileName().toString();
        int dot = filename.lastIndexOf('.');

        String newName = (dot == -1)
                ? filename + suffix
                : filename.substring(0, dot) + suffix;

        return t.resolveSibling(newName);
    }

    private static class Failure {
        private Throwable throwable;
        private String file;

        public Failure(Throwable throwable, String file) {
            this.throwable = throwable;
            this.file = file;

        }
        public Throwable getThrowable() {
            return throwable;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
        }

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }
    }

}
