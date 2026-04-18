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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileAsserter {

    public static void resourceAssert(String expected, String actual) throws IOException {
        InputStream e = FileAsserter.class.getResourceAsStream(expected);
        scriptAssert(e, actual, expected);
    }

    public static void scriptAssert(InputStream expected, String actual, String msg) throws IOException {

        List<String> expectedLines = convert(expected);
        List<String> actuaLines = Arrays.asList(actual.split("\n"));
        scriptAssert(expectedLines, actuaLines, msg);
    }

    public static void scriptAssert(String expected, String actual, String msg) {

        //List<String> expectedLines = convert(expected);
        List<String> expectedLines = Arrays.asList(expected.split("\n"));
        List<String> actuaLines = Arrays.asList(actual.split("\n"));
        scriptAssert(expectedLines, actuaLines, msg);
    }

    public static String string(InputStream in) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        while ((length = in.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(StandardCharsets.UTF_8.name());
    }


    public static List<String> convert(InputStream in) {
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.toList());
    }

    public static void scriptAssert(Path expected, Path actual, String msg) throws IOException {

        List<String> expectedLines = Files.readAllLines(expected);
        List<String> actuaLines = Files.readAllLines(actual);
        scriptAssert(expectedLines, actuaLines, msg);
    }

    public static void scriptAssert(String expected, String actual) {

        List<String> expectedLines = Arrays.asList(expected.split("\n"));
        List<String> actuaLines = Arrays.asList(actual.split("\n"));
        scriptAssert(expectedLines, actuaLines);
    }

    public static void scriptAssert(List<String> expected, List<String> actual) {
        scriptAssert(expected, actual, null);
    }

    public static void scriptAssert(List<String> expected, List<String> actual, String msg) {

        assertEquals(expected.size(), actual.size(), "diff in size");

        int line = 0;
        while (line < expected.size()) {

            String e = expected.get(line);
            String a = actual.get(line);

            a = strip(a);
            e = strip(e);

            assertEquals(e, a, "diff in line " + (line+1) + " " + (msg != null ? msg : ""));
            line++;
        }
    }

    private static String strip(String a) {
        if (a.endsWith("\r")) {
            a = a.substring(0, a.length() - 1);
        }
        return a;
    }

    public static Path getSibling(Path file, String ext, String target) {
        return file.resolveSibling(file.getFileName().toString().replace(ext, target));
    }

}
