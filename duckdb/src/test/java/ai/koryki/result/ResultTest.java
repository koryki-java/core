package ai.koryki.result;

import ai.koryki.antlr.AbstractReader;
import ai.koryki.catalog.Util;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.databases.northwind.duckdb.NorthwindDuckdb;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.kql.Engine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


import java.io.File;
import java.time.ZoneId;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

public class ResultTest {

    private static NorthwindService serviceEn;
    private static NorthwindService serviceDe;

    @BeforeAll
    public static void init() {
        serviceEn = new NorthwindService(NorthwindDuckdb.northwind(), new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")), Finding::new);
        serviceDe = new NorthwindService(NorthwindDuckdb.northwind(), new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")), Locale.GERMAN, Finding::new);
    }

    @Test
    public void ordersfromgermany() {

        ListResult r = test("ordersfromgermany");

        assertEquals(3, r.getInfos().size());
        Finding f = r.getInfos().get(0);
        assertEquals("Customer Name", f.getHeader());

        f = r.getInfos().get(2);
        assertEquals(CoreTypeFamily.TEXT, f.getFallbackFamily());
        assertEquals("character varying", f.getDialectType());
        assertEquals("Customer City", f.getHeader());
        assertFalse(f.isAggregate());
        assertNull(f.getMath());

        assertEquals(CoreTypeFamily.TEXT, f.getFallbackFamily());
    }

    @Test
    public void customercount() {

        ListResult r = test("customercount");
        assertEquals(1, r.getInfos().size());
        assertEquals("Count", r.getInfos().get(0).getMetric());
    }

    @Test
    public void germancustomercount() {

        ListResult r = test("germancustomercount");
        assertEquals(1, r.getInfos().size());
    }

    @Test
    public void berlincustomercount() {

        ListResult r = test("berlincustomercount");
        assertEquals(1, r.getInfos().size());
    }
    @Test
    public void umsatz_je_kategorie_und_monat() {

        ListResult r = test("umsatz_je_kategorie_und_monat", serviceDe);


        assertNull( r.getInfos().get(0).getOut().getHeader());
        assertEquals("Kategorie Name", r.getInfos().get(0).getHeader());
        assertEquals("monat", r.getInfos().get(1).getOut().getHeader());
        assertEquals("month(Bestellung Bestelldatum)", r.getInfos().get(1).getHeader());
        assertEquals("umsatz", r.getInfos().get(2).getOut().getHeader());
        assertEquals("Summe(Bestellposition Preis je Einheit*Bestellposition Menge*{number}-Bestellposition Rabatt)", r.getInfos().get(2).getHeader());
        // metric concluded from the shape (localized), mechanical header above untouched
        assertEquals("Nettoumsatz", r.getInfos().get(2).getMetric());
        assertNull(r.getInfos().get(0).getMetric());
        assertEquals(3, r.getInfos().size());
    }

    @Test
    public void umsatz_je_kategorie_und_monat1() {

        ListResult r = test("umsatz_je_kategorie_und_monat1");


        assertNull( r.getInfos().get(0).getOut().getHeader());
        assertEquals("mr", r.getInfos().get(1).getOut().getExpression().getField().getAlias());
        assertEquals("mon", r.getInfos().get(1).getOut().getExpression().getField().getName());

        assertEquals("Category Name", r.getInfos().get(0).getHeader());
        assertEquals("month(Order Order Date)", r.getInfos().get(1).getHeader());
        assertEquals("Sum(Order Details Unit Price*Order Details Quantity*{number}-Order Details Discount)", r.getInfos().get(2).getHeader());
        assertEquals("lag(Sum(Order Details Unit Price*Order Details Quantity*{number}-Order Details Discount))", r.getInfos().get(3).getHeader());
        assertEquals("Sum(Order Details Unit Price*Order Details Quantity*{number}-Order Details Discount)-lag(Sum(Order Details Unit Price*Order Details Quantity*{number}-Order Details Discount))", r.getInfos().get(4).getHeader());

        // metrics through the CTE: shapes propagate, lag delta concluded as change
        assertEquals("Net revenue", r.getInfos().get(2).getMetric());
        assertNull(r.getInfos().get(3).getMetric());
        assertEquals("Change", r.getInfos().get(4).getMetric());
    }


    @Test
    public void anzahldeutschekunden() {

        ListResult r = test("anzahldeutschekunden", serviceDe);
        assertEquals(1, r.getInfos().size());
    }

    private static ListResult test(String query) {
        return test(query, serviceEn);
    }

    private static ListResult test(String query, NorthwindService service) {
        String kql = AbstractReader.readResource("/ai/koryki/databases/northwind/result/kql/" + query + ".kql");

        Engine engine = service.getEngine();
        LinkResolver resolver = engine.getResolver();
        Investigator investigator = new Investigator(service.getEngine().getResolver().getLocale());
        engine.setInfo(investigator.asInfoProvider());
        ListResult r = (ListResult) engine.executeKQL(kql, ListResult::new);
        System.out.println(r.toCSV());
        Util.write(r.getInfos(), new File("build/" + query+ "_" + resolver.getLocale().getLanguage() + ".json"));
        return r;
    }


}
