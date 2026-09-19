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
package ai.koryki.iql;

import ai.koryki.antlr.Position;
import ai.koryki.antlr.Range;
import ai.koryki.antlr.RangeException;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
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
