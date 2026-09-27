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

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Where SQL is executed and where its rows come from.
 *
 * <p>There are two kinds of implementation, and this interface serves both:
 *
 * <ul>
 *   <li>a <b>JDBC</b> database, which prepares statements on a {@link java.sql.Connection} --
 *       {@link JdbcDatabase} and the eight dialects on top of it. It implements the two {@code
 *       execute} methods that take JDBC types and inherits {@link #executeInto} unchanged.
 *   <li>a database that is <b>not</b> reached through JDBC -- a remote service that takes one
 *       statement and returns rows. It implements {@link #executeInto} and nothing else; there is
 *       no statement it could hand out, and it should not have to invent one.
 * </ul>
 *
 * <p><b>Why the entry point has a name of its own.</b> {@code execute(String, C)} would have been
 * the obvious spelling, and it would have broken every caller of {@code execute(String,
 * Supplier<C>)}: {@link ResultConsumer} has a single abstract method, so a lambda or a constructor
 * reference such as {@code ListResult::new} fits both overloads and the call no longer compiles.
 *
 * <p><b>What an implementation not built on JDBC owes the processor.</b> Call {@code
 * ResultProcessor.append} once per row, in column order, and stop when it returns {@code false}.
 * Values arrive as the read layer's own types (the {@code java.time} classes, {@code BigDecimal},
 * {@code Long}, {@code Double}, {@code String}, {@code Boolean}), because that is what {@link
 * ColumnInfo#getTypeDescriptor} and the formatting downstream expect -- {@link JdbcDatabase} shows
 * how a JDBC value becomes one. {@code ResultProcessor.metadata} takes a {@link
 * java.sql.ResultSetMetaData} and is never called: the columns come from {@code getInfos()}, which
 * the engine fills from the query itself. Such a database therefore declares {@code C extends
 * ResultProcessor<?>}, as {@link JdbcDatabase} does; this interface is bound wider because a
 * consumer that only wants column metadata needs no rows.
 *
 * <p>Whether two calls may overlap is the database's to say, through {@link
 * #allowsConcurrentExecution}: a {@link JdbcDatabase} wraps one connection and its callers have to
 * serialize; a database that can run in parallel says so, and its callers stop doing it.
 */
public interface Database<C extends ResultConsumer<?>> extends AutoCloseable {

    @Override
    void close() throws SQLException;

    /**
     * Runs {@code sql} and hands every row to {@code processor}. The one method a database that is
     * not JDBC has to implement, and the one the engine calls.
     *
     * <p>The default is the JDBC route -- prepare, then {@link #execute(PreparedStatement,
     * ResultConsumer)} -- so {@link JdbcDatabase} and every implementation written against the two
     * methods below behave exactly as they did before this method existed.
     *
     * <p>The processor is not closed here; whoever created it does that ({@link #execute(String,
     * Supplier)} does).
     */
    default void executeInto(String sql, C processor) {

        execute(sql, s -> execute(s, processor));
    }

    /**
     * Runs {@code sql} into a processor created for this one call, and closes it afterwards.
     *
     * @return the processor, holding the rows
     */
    default C execute(String sql, Supplier<C> processor) {

        try (C p = processor.get()) {
            executeInto(sql, p);
            return p;
        }
    }

    /**
     * Whether two threads may be inside {@link #executeInto} at the same time, each with a
     * processor of its own.
     *
     * <p>{@code false} unless an implementation says otherwise, and that is the answer for {@link
     * JdbcDatabase}: it wraps one {@link java.sql.Connection}, and statements running in parallel
     * on one connection are not safe. A caller that holds a database like that has to run its calls
     * one after the other, and this method is how it finds out -- it is why the default is the
     * cautious answer.
     *
     * <p>A database that takes one statement per call and keeps nothing between two of them -- a
     * stateless service -- overrides this with {@code true}. Its callers then have no reason to
     * queue: waiting for someone else's round trip would only make every user as slow as the
     * slowest.
     *
     * <p>This is a statement about the database alone. It does not make the processors safe to
     * share: every call brings its own, and a processor is written by one thread. Nor does it make
     * the rest of a call safe: calls that overlap must each render on a renderer of their own,
     * because a {@code SqlRenderer} keeps what it renders in fields. An {@code Engine} built from a
     * renderer supplier does that by itself; one built from a renderer instance (deprecated) does
     * not. The reason to say so here is that the lock this method takes away used to hide it.
     */
    default boolean allowsConcurrentExecution() {
        return false;
    }

    /**
     * JDBC only: prepares {@code sql} and hands the statement to {@code consumer}, then closes it.
     *
     * <p>Defaults to refusing, so that a database with no statements does not have to implement a
     * method it cannot honour. The message names the way out.
     *
     * @throws UnsupportedOperationException on a database that is not JDBC
     */
    default void execute(String sql, Consumer<PreparedStatement> consumer) {
        throw notJdbc();
    }

    /**
     * JDBC only: runs a prepared statement and hands its rows to {@code processor}.
     *
     * @throws UnsupportedOperationException on a database that is not JDBC
     */
    default void execute(PreparedStatement statement, C processor) {
        throw notJdbc();
    }

    /**
     * The refusal, worded once. An implementation that overrides neither {@link #executeInto} nor
     * the two JDBC methods lands here, and the message has to say which of them it was meant to
     * write -- the stack trace alone points at this interface, not at the class that forgot.
     */
    private UnsupportedOperationException notJdbc() {
        return new UnsupportedOperationException(
                getClass().getName()
                        + " is not a JDBC database: it has no statements to prepare or run. "
                        + "A database that is not JDBC implements executeInto(sql, processor); a "
                        + "JDBC one implements execute(sql, Consumer<PreparedStatement>) and "
                        + "execute(PreparedStatement, processor).");
    }
}
