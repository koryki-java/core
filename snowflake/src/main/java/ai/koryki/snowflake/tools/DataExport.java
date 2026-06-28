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
package ai.koryki.snowflake.tools;

import ai.koryki.jdbc.XMLFileResult;
import ai.koryki.kql.Engine;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;

import java.io.File;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DataExport<C extends XMLFileResult<?>> {

    private final Engine<?, C> engine;
    private final Function<File, C> resultFactory;

    public DataExport(Engine<?, C> engine, Function<File, C> resultFactory) {
        this.engine = engine;
        this.resultFactory = resultFactory;
    }

    public void exportData(File dir) {

        if (!dir.exists()) {
            dir.mkdirs();
        }

        ((Schema)engine.getResolver().getSchema()).getTables().forEach(t -> exportTable(dir, t));
    }

    public void exportTable(File dir, Table table) {

        String alias = "a";
        String fetch = table.getColumns().stream().map(c -> alias + "." + c.getName().toLowerCase()).collect(Collectors.joining(", "));

        String kql = "FIND " + table.getName().toLowerCase() + " " + alias + System.lineSeparator();
        kql += "FETCH " + fetch;
        //kql += " LIMIT 10";
        try {

            long start = System.currentTimeMillis();
            File file = new File(dir, table.getName() + ".xml");
            System.out.print(file);
            System.out.flush();

            C result = engine.executeKQL(kql, () -> resultFactory.apply(file));

            System.out.println(" " +  result.getFile().length() + " " + (System.currentTimeMillis() - start));
        } catch (RuntimeException e) {
            System.out.println("failed to export: " + table.getName());
            System.out.println(kql);
            e.printStackTrace(System.out);
        }
    }


}
