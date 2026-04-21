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
package ai.koryki.postgresql;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class Psql {

    private String log = "";

    public int runScript(File script, boolean stop) throws IOException, InterruptedException {

//        postgres.northwind.url=jdbc:postgresql://johannes-x600:5432/northwind
//        postgres.northwind.user=PG
//        postgres.northwind.password=JZ_123


        System.setProperty("PGPASSFILE", "~/.pgpass");

        String commandBase = "psql " + (stop ? "-v ON_ERROR_STOP=1" : "") + " -U PG -h johannes-x600 -d northwind -f ";
        String command = commandBase + script.getCanonicalPath();

        long start = System.currentTimeMillis();
        System.out.println();
        System.out.println("start: " + script.getCanonicalPath());
        Process process = Runtime.getRuntime().exec(command);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log += line + System.lineSeparator();
                System.out.println(line);
            }
        }
        int ret = process.waitFor();
        long d = System.currentTimeMillis() - start;
        System.out.printf("Exited with code: %d ms: %d%n" , ret, d);
        return ret;
    }

    public String getLog() {
        return log;
    }

}
