package ai.koryki.iql;

import ai.koryki.antlr.Position;
import ai.koryki.antlr.Range;
import ai.koryki.antlr.RangeException;
import ai.koryki.databases.northwind.NorthwindService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.fail;

public class ResolverTest {

    private static LinkResolver resolver;

    @BeforeAll
    public static void readNorthwindDB() throws IOException {

        resolver = NorthwindService.resolver();
    }

    @Test
    public void nonUniqueLink() {

        try {
            Range range = new Range(new Position(1, 1), new Position(2, 2));
            Optional<String> l = resolver.findLink(range, "categories", "categories");

            System.out.println("ResolverTest " + l.isPresent());
            fail();
        } catch (RangeException e) {

            System.out.println("ResolverTest " + e.getMessage());
        }

    }

    @Test
    public void parentOf() {
        Range range = new Range(new Position(1, 1), new Position(2, 2));
        String link = "parent_of";
        Optional<String> l = resolver.findLink(range, "categories", "categories", link);
        System.out.println("ResolverTest " + link + ": " + l.isPresent());
    }

    @Test
    public void childOf() {
        Range range = new Range(new Position(1, 1), new Position(2, 2));
        String link = "child_of";
        Optional<String> l = resolver.findLink(range, "categories", "categories", link);
        System.out.println("ResolverTest " + link + ": " + l.isPresent());
    }

    @Test
    public void invalid() {
        try {
            Range range = new Range(new Position(1, 1), new Position(2, 2));
            String link = "invalid";
            Optional<String> l = resolver.findLink(range, "categories", "categories", link);
            System.out.println("ResolverTest " + link + ": " + l.isPresent());
        } catch (RangeException e) {

            System.out.println("ResolverTest " + e.getMessage());
        }
    }
}
