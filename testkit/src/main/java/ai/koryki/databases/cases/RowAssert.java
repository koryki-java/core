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
package ai.koryki.databases.cases;

import ai.koryki.kql.Engine;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.catalog.Util;
import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.jdbc.ListResult;

import java.io.File;
import java.sql.SQLException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RowAssert implements TestCase {
    private int rows;
    private String kql;
    private final Engine<ColumnInfo, ListResult<ColumnInfo>> engine;
    private String name;


    public RowAssert(Engine<ColumnInfo, ListResult<ColumnInfo>> engine, String kql, int rows) throws SQLException {
        this(engine, kql, rows, null);
    }

    public RowAssert(Engine<ColumnInfo, ListResult<ColumnInfo>> engine, String kql, int rows, String name) throws SQLException {
        // An engine is immutable: rather than reconfiguring the one passed in, a second one is
        // built here that differs only in its column description.
        this.engine = engine.withInfo(t -> t.infos(HeaderInfo::new));
        this.kql = kql;
        this.rows = rows;
        this.name = name;

        run();
    }

    @Override
    public void run() throws SQLException {
        Supplier<ListResult<ColumnInfo>> processor = () -> build();

        ListResult<ColumnInfo> result = engine.executeKQL(kql, processor);
        if (name != null) {
            Util.text(result.toCSV(), new File("build/" + name + ".csv"));
        }
        assertEquals(rows, result.getRows().size());

    }

    private ListResult<ColumnInfo> build() {



        //List<ColumnInfo> infos = transpiler.infos(HeaderInfo::new);
        ListResult<ColumnInfo> result = new ListResult<ColumnInfo>();
        if (name != null) {
          //  result.setInfos(infos);
        }
        return result;
    }

}
