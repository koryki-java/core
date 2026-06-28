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

import ai.koryki.jdbc.Database;
import ai.koryki.jdbc.ListResult;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;

import java.sql.SQLException;

//import ai.koryki.catalog.schema.Type;

public class SchemaExport {

    private final Database<ListResult<?>> information;

    public SchemaExport(Database<ListResult<?>> information) {

        this.information = information;
    }

    public Schema readSchema(String schemaname) throws SQLException {

        Schema schema = new Schema();

        readTables(schemaname, schema);
        //readFK(schemaname);

        return schema;
    }

    private void readTables(String schemaname, Schema schema) {
        String sql =
"""
SELECT
  table_name,
  comment
FROM information_schema.tables
WHERE table_schema = '%s';
""".formatted(schemaname);

        ListResult<?> r = information.execute(sql, ListResult::new);


        r.getRows().forEach(row -> {
            Table t = new Table();
            String name = row.get(0).toString();
            t.setName(name.toLowerCase());
            t.setComment(row.get(1) != null ? row.get(1).toString() : null);
            schema.getTables().add(t);

            readColumns(schemaname, name, t);
        });
    }

    private void readColumns(String schemaname, String name, Table table) {

        String sql = """
SELECT
       column_name,
       comment,
       data_type,
       is_nullable,
       ordinal_position
FROM information_schema.columns
WHERE table_schema = '%s'
AND table_name = '%s'
ORDER BY table_name, ordinal_position;
                """.formatted(schemaname, name);

        ListResult<?> r = information.execute(sql, ListResult::new);
        r.getRows().forEach(row -> {
            Column c = new Column();
            c.setName(row.get(0).toString().toLowerCase());
            c.setComment(row.get(1) != null ? row.get(1).toString() : null);
            c.setTypeFamily(row.get(2).toString());
            //c.setDbType();
            //Type t = new Type();
            //t.setName(row.get(2).toString());
            //c.setType(t);
            c.setNullable(Boolean.valueOf(row.get(3).toString()));
            table.getColumns().add(c);
        });

    }

    private void readPK() {
        String sql = """
SELECT
  tc.table_name,
  kcu.column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
  ON tc.constraint_name = kcu.constraint_name
WHERE tc.constraint_type = 'PRIMARY KEY'
  AND tc.table_schema = 'MY_SCHEMA';
        """        ;

    }

//    private void readFK(String schemaname) {
//String sql = """
//SELECT
//  tc.table_name,
//  kcu.column_name,
//  ccu.table_name AS referenced_table,
//  ccu.column_name AS referenced_column
//FROM information_schema.table_constraints tc
//JOIN information_schema.key_column_usage kcu
//  ON tc.constraint_name = kcu.constraint_name
//JOIN information_schema.constraint_column_usage ccu
//  ON ccu.constraint_name = tc.constraint_name
//WHERE tc.constraint_type = 'FOREIGN KEY'
//  AND tc.table_schema = '%s';
//        """.formatted(schemaname)        ;
//        ListResult r = information.execute(sql, ListResult::new);
//
//    }
}
