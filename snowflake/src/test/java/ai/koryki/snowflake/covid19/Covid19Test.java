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
package ai.koryki.snowflake.covid19;

import ai.koryki.antlr.AbstractReader;
import ai.koryki.kql.Engine;
//import ai.koryki.databases.samples.*;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.scaffold.Util;
import ai.koryki.scaffold.schema.Schema;
import ai.koryki.snowflake.SnowflakeUnavailable;
import ai.koryki.snowflake.iql.SqlQueryRenderer;
import ai.koryki.snowflake.tools.DataExport;
import ai.koryki.snowflake.tools.SchemaExport;
import ai.koryki.jdbc.ListResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.sql.*;
import java.util.Arrays;
import java.util.List;


@SnowflakeUnavailable
public class Covid19Test {

    private static Covid19Service< ListResult<HeaderInfo>> service;

    @BeforeAll
    public static void readCovid19DB() throws Exception {

        long start = System.currentTimeMillis();
        service = new Covid19Service<ListResult<HeaderInfo>>(new Covid19Database<>(), new SqlQueryRenderer());

        System.out.println("loading " + " : " + (System.currentTimeMillis() - start));
    }

    @AfterAll
    public static void shutdown() throws SQLException {
        if (service != null && service.getEngine() != null &&  service.getEngine().getDatabase() != null) {
            service.getEngine().getDatabase().close();
        }
    }

    public static final String ROOT = "/ai/koryki/databases/covid19/snowflake";

    @Test
    public void kql() throws SQLException {

        long start = System.currentTimeMillis();
        String k = AbstractReader.readResource(ROOT + "/kql/global.kql");
        ListResult<?> r = service.getEngine().executeKQL(k, () -> new ListResult<>());
        long end = System.currentTimeMillis() - start;
        System.out.println(r.toCSV());
        System.out.println("duration " + end);
    }

    @Test
    public void xml2csv() throws Exception {

        if (true) {
            return;
        }

        File dir = new File("src/data");
        List<File> files = Arrays.asList(dir.listFiles());

        files.forEach(f -> convert(f));

        //File test = new File("src/data/cdc_inpatient_beds_icu_all.xml");
        //convert(test);
    }

    private void convert(File xml)  {

        SAXParserFactory factory = SAXParserFactory.newInstance();

// Sicherheit + Speicher
        factory.setNamespaceAware(true);
        factory.setValidating(false);

        try {
// XXE & Entity-Bomben verhindern
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            SAXParser saxParser = factory.newSAXParser();


            String path = xml.getName();
            File csv = new File(new File("build"),path.substring(0, path.length() - 4) + ".csv");
            try (InputStream in = new BufferedInputStream(
                    new FileInputStream(xml), 64 * 1024); PrintWriter w = new PrintWriter(new FileWriter(csv))) {


                System.out.print(xml + " ");
                long start = System.currentTimeMillis();
                saxParser.parse(in, new StreamingHandler(w));
                System.out.print(" " + (System.currentTimeMillis() - start));
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (SAXNotRecognizedException e) {
            throw new RuntimeException(e);
        } catch (SAXNotSupportedException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        }
    }



    @Test
    public void schemaExport() throws Exception {

        if (true) {
            return;
        }

        Covid19Database db = new Covid19Database();
        SchemaExport e = new SchemaExport(db);
        Schema s = e.readSchema("PUBLIC");
        Util.write(s, new File(new File("build"), "schema.json"));

        s.getTables().forEach(t -> System.out.println(t.getName() + " " + t.getComment()));
    }

    @Test
    public void dataExport() throws Exception {

        if (true) {
            return;
        }

        Covid19Database db = new Covid19Database();
        Engine e = Engine.build (db, Covid19Service.resolver(), new SqlQueryRenderer());

        DataExport export = new DataExport(e);
        File data = new File (new File("build"), "data");
        export.exportData(data);
    }



    @Test
    public void global() throws SQLException {
        String sql = """
                SELECT   COUNTRY_REGION, SUM(CASES) AS Cases
                FROM     ECDC_GLOBAL
                GROUP BY COUNTRY_REGION;
                """;

        ListResult r = service.getEngine().getDatabase().execute(sql, () -> new ListResult());

        System.out.println(r.toCSV());
    }

}
