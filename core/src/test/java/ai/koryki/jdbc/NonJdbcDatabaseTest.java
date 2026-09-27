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
package ai.koryki.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.kql.Engine;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.kql.HeaderInfo;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * A {@link Database} that is not JDBC: nothing to prepare, no {@code ResultSet} to read, and yet
 * the engine runs KQL on it.
 *
 * <p>The two doubles are the two ways a database can be written. {@link ServiceDatabase} is the new
 * one -- {@code executeInto} and {@code close}, no JDBC type anywhere in it -- and stands for a
 * service that takes a statement and returns rows. {@link StatementOnlyDatabase} is every
 * implementation that existed before {@code executeInto}: only the two methods that take JDBC
 * types. It has to keep working unchanged, which is what its tests hold.
 */
class NonJdbcDatabaseTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    private static final String DB = "/ai/koryki/databases/northwind";
    private static final String MODEL = DB + "/model";

    /** Implements {@code executeInto} and {@code close}, and nothing that mentions JDBC. */
    private static final class ServiceDatabase<C extends ResultProcessor<?>>
            implements Database<C> {

        final List<String> statements = new ArrayList<>();
        int columnsAnnounced = -1;
        private final List<List<Object>> rows;
        private final boolean concurrent;

        ServiceDatabase(List<List<Object>> rows) {
            this(rows, false);
        }

        ServiceDatabase(List<List<Object>> rows, boolean concurrent) {
            this.rows = rows;
            this.concurrent = concurrent;
        }

        @Override
        public boolean allowsConcurrentExecution() {
            return concurrent;
        }

        @Override
        public void executeInto(String sql, C processor) {
            statements.add(sql);
            // What a database with no ResultSetMetaData types its values by: the columns the engine
            // has already put on the processor.
            columnsAnnounced = processor.getInfos() == null ? 0 : processor.getInfos().size();
            for (List<Object> row : rows) {
                if (!processor.append(row)) {
                    break;
                }
            }
        }

        @Override
        public void close() {}
    }

    /**
     * Written the way every {@code Database} was before {@code executeInto}: the two methods that
     * take JDBC types, nothing else. The statement is a stand-in that fails on any call -- nothing
     * here may read it.
     */
    private static final class StatementOnlyDatabase<C extends ResultProcessor<?>>
            implements Database<C> {

        private static final PreparedStatement STATEMENT =
                (PreparedStatement)
                        Proxy.newProxyInstance(
                                NonJdbcDatabaseTest.class.getClassLoader(),
                                new Class<?>[] {PreparedStatement.class},
                                (proxy, method, args) -> {
                                    throw new UnsupportedOperationException(method.getName());
                                });

        final List<String> prepared = new ArrayList<>();

        @Override
        public void close() {}

        @Override
        public void execute(String sql, Consumer<PreparedStatement> statementConsumer) {
            prepared.add(sql);
            statementConsumer.accept(STATEMENT);
        }

        @Override
        public void execute(PreparedStatement statement, C processor) {
            processor.append(List.<Object>of("via the statement"));
        }
    }

    private static LinkResolver resolver() {
        return new LinkResolver(
                Locale.ENGLISH,
                CatalogLoader.db(DB),
                CatalogLoader.model(MODEL, Locale.ENGLISH),
                true);
    }

    private static Engine<HeaderInfo, ListResult<HeaderInfo>> engineOver(
            Database<ListResult<HeaderInfo>> database) {
        return EngineBuilder.headers(
                        database,
                        resolver(),
                        () -> new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, UTC))
                .build();
    }

    @Test
    void anEngineRunsKqlOnADatabaseThatHasNoStatements() {
        ServiceDatabase<ListResult<HeaderInfo>> database =
                new ServiceDatabase<>(List.of(List.<Object>of("Alfreds Futterkiste")));

        ListResult<HeaderInfo> result =
                engineOver(database)
                        .executeKQL("FIND customers c FETCH c.company_name name", ListResult::new);

        assertEquals(List.of(List.of("Alfreds Futterkiste")), result.getRows());
        assertEquals(1, database.statements.size());
        assertTrue(database.statements.get(0).contains("customers c"), database.statements.get(0));
    }

    /**
     * The point of the KQL path on such a database: the columns are known from the query, before a
     * single row arrives, because there is no result set to ask.
     */
    @Test
    void theColumnsAreOnTheProcessorBeforeTheFirstRow() {
        ServiceDatabase<ListResult<HeaderInfo>> database =
                new ServiceDatabase<>(List.of(List.<Object>of("Alfreds Futterkiste")));

        ListResult<HeaderInfo> result =
                engineOver(database)
                        .executeKQL("FIND customers c FETCH c.company_name name", ListResult::new);

        assertEquals(1, database.columnsAnnounced);
        assertEquals("name", result.getInfos().get(0).getHeader());
        assertNotNull(result.getInfos().get(0).getTypeDescriptor());
    }

    @Test
    void rawSqlAndRunSqlReachTheSameEntryPoint() {
        ServiceDatabase<ListResult<HeaderInfo>> database =
                new ServiceDatabase<>(List.of(List.<Object>of(1L)));
        Engine<HeaderInfo, ListResult<HeaderInfo>> engine = engineOver(database);

        engine.executeSQL("SELECT 1", ListResult::new);
        ListResult<HeaderInfo> collected = engine.runSql("SELECT 2");

        assertEquals(List.of("SELECT 1", "SELECT 2"), database.statements);
        assertEquals(List.of(List.of(1L)), collected.getRows());
    }

    /**
     * A statement hook configures a JDBC statement, and there is none. Silently ignoring it would
     * let a caller believe a timeout or fetch size was in force.
     */
    @Test
    void aStatementHookIsRefusedWhereThereIsNoStatement() {
        ServiceDatabase<ListResult<HeaderInfo>> database = new ServiceDatabase<>(List.of());

        UnsupportedOperationException e =
                assertThrows(
                        UnsupportedOperationException.class,
                        () ->
                                engineOver(database)
                                        .executeSQL("SELECT 1", ListResult::new, statement -> {}));

        assertTrue(e.getMessage().contains(ServiceDatabase.class.getName()), e.getMessage());
        assertTrue(e.getMessage().contains("executeInto"), e.getMessage());
        assertTrue(database.statements.isEmpty(), "nothing may have run");
    }

    /**
     * What an implementation gets for writing neither way: a message that says which ways exist.
     */
    @Test
    void aDatabaseThatImplementsNeitherWayExplainsWhatItIsMissing() {
        Database<ListResult<HeaderInfo>> nothing = () -> {};

        UnsupportedOperationException e =
                assertThrows(
                        UnsupportedOperationException.class,
                        () -> nothing.executeInto("SELECT 1", new ListResult<>()));

        assertTrue(e.getMessage().contains("executeInto"), e.getMessage());
        assertTrue(e.getMessage().contains("PreparedStatement"), e.getMessage());
    }

    @Test
    void aDatabaseWrittenAgainstTheTwoJdbcMethodsStillWorks() {
        StatementOnlyDatabase<ListResult<HeaderInfo>> statements = new StatementOnlyDatabase<>();
        Database<ListResult<HeaderInfo>> database = statements;

        // Also the compile-time guard: had the entry point been an overload of execute(String, C),
        // this call would be ambiguous between it and execute(String, Supplier<C>).
        ListResult<HeaderInfo> viaSupplier = database.execute("SELECT 1", ListResult::new);
        ListResult<HeaderInfo> viaInto = new ListResult<>();
        database.executeInto("SELECT 2", viaInto);

        assertEquals(List.of("SELECT 1", "SELECT 2"), statements.prepared);
        assertEquals(List.of(List.of("via the statement")), viaSupplier.getRows());
        assertEquals(List.of(List.of("via the statement")), viaInto.getRows());
    }

    @Test
    void theEngineStillReachesADatabaseWrittenTheOldWay() {
        StatementOnlyDatabase<ListResult<HeaderInfo>> statements = new StatementOnlyDatabase<>();

        ListResult<HeaderInfo> result =
                engineOver(statements)
                        .executeKQL("FIND customers c FETCH c.company_name name", ListResult::new);

        assertEquals(1, statements.prepared.size());
        assertEquals(List.of(List.of("via the statement")), result.getRows());
    }

    @Test
    void theStatementHookStillReachesADatabaseWrittenTheOldWay() {
        StatementOnlyDatabase<ListResult<HeaderInfo>> statements = new StatementOnlyDatabase<>();
        List<PreparedStatement> hooked = new ArrayList<>();

        engineOver(statements)
                .executeSQL(
                        "SELECT 1",
                        ListResult::new,
                        statement -> hooked.add((PreparedStatement) statement));

        assertEquals(1, hooked.size(), "the hook must have been offered the statement");
    }

    /**
     * The cautious answer is the default: a database that says nothing -- one written before the
     * method existed, or one that only implements {@code close} -- is one its callers have to
     * serialize.
     */
    @Test
    void aDatabaseIsSerializedUnlessItSaysOtherwise() {
        Database<ListResult<HeaderInfo>> silent = () -> {};

        assertFalse(silent.allowsConcurrentExecution());
        assertFalse(
                new StatementOnlyDatabase<ListResult<HeaderInfo>>().allowsConcurrentExecution());
        assertFalse(
                new ServiceDatabase<ListResult<HeaderInfo>>(List.of()).allowsConcurrentExecution());
    }

    /**
     * The reason for the default: one connection, and statements in parallel on it are not safe.
     */
    @Test
    void aJdbcDatabaseIsSerializedBecauseItWrapsOneConnection() {
        Connection connection =
                (Connection)
                        Proxy.newProxyInstance(
                                NonJdbcDatabaseTest.class.getClassLoader(),
                                new Class<?>[] {Connection.class},
                                (proxy, method, args) -> {
                                    throw new UnsupportedOperationException(method.getName());
                                });

        assertFalse(
                new JdbcDatabase<ListResult<HeaderInfo>>("one connection", connection, UTC)
                        .allowsConcurrentExecution());
    }

    @Test
    void aDatabaseCanSayItAllowsOverlappingCalls() {
        assertTrue(
                new ServiceDatabase<ListResult<HeaderInfo>>(List.of(), true)
                        .allowsConcurrentExecution());
    }
}
