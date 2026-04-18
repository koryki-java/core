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
package ai.koryki.kql;

import ai.koryki.antlr.AbstractReader;
import ai.koryki.antlr.Interval;
import ai.koryki.antlr.MsgErrorListener;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class KQLReader extends AbstractReader<KQLLexer, KQLParser, KQLParser.QueryContext> {

    private KQLLexer lexer;
    private KQLParser parser;
    private CharStream cs;
    private LineNumberReader lnr;
    private List<Interval> panic = new ArrayList<>();

    private BufferedTokenStream tokens;
    private KQLParser.QueryContext script;
    boolean abort;

    public static String kqlDefinition() {
        return readResource( "/ai/koryki/kql/KQL.g4");
    }

    public KQLReader(String sql) throws IOException {

        this(new StringReader(sql), false);
    }

    public KQLReader(String sql, boolean abort) throws IOException {

        this(new StringReader(sql), abort);
    }

    public String getDescription() {
        return getComment(getQuery());
    }

    public KQLReader(File in) throws IOException {
        this(new FileInputStream(in), StandardCharsets.UTF_8);
    }

    public KQLReader(InputStream in, boolean abort) throws IOException {
        this(in, StandardCharsets.UTF_8, abort);
    }

    public KQLReader(InputStream in) throws IOException {
        this(in, StandardCharsets.UTF_8);
    }

    public KQLReader(InputStream in, Charset cs) throws IOException {

        this(new InputStreamReader(in, cs), false);
    }

    public KQLReader(InputStream in, Charset cs, boolean abort) throws IOException {

        this(new InputStreamReader(in, cs), abort);
    }

    public KQLReader(Reader in, boolean abort) throws IOException {
        lnr = new LineNumberReader(in);
        this.cs = CharStreams.fromReader(lnr);
        this.abort = abort;
    }

    public KQLReader(CharStream input, boolean abort) {

        cs = input;
        this.abort = abort;
    }

    public KQLReader(BufferedTokenStream tokens, List<Interval> panic, KQLParser.QueryContext script) {

        this.tokens = tokens;
        this.panic =  panic;
        this.script = script;
    }

    private long lexduration;
    private long parseduration;

    private void parse() {
        if (script != null) {
            return;
        }
        lex();
        // parsing
        parser = new KQLParser(tokens);
        parser.removeErrorListeners();
        MsgErrorListener listener = new MsgErrorListener(abort);
        parser.addErrorListener(listener);
        long start = System.currentTimeMillis();
        script = parser.query();
        parseduration = System.currentTimeMillis() - start;
        panic.addAll(listener.getPanic());
    }


    private void lex() {
        if (tokens != null) {
            return;
        }
        long start = System.currentTimeMillis();

        lexer = new KQLLexer(cs);
        lexer.removeErrorListeners();
        MsgErrorListener listener = new MsgErrorListener(abort);
        lexer.addErrorListener(listener);
        tokens = new CommonTokenStream(lexer);
        lexduration = System.currentTimeMillis() - start;
        panic.addAll(listener.getPanic());
    }

    @Override
    public KQLLexer getLexer() {
        if (tokens == null) {
            lex();
        }
        return lexer;
    }

    public BufferedTokenStream getTokens() {
        if (tokens == null) {
            lex();
        }
        return tokens;
    }

    @Override
    public KQLParser getParser() {
        if (parser == null) {
            parse();
        }
        return parser;
    }

    @Override
    public KQLParser.QueryContext getCtx() {
        return getQuery();
    }

    public KQLParser.QueryContext getQuery() {
        if (script == null) {
            parse();
        }
        return script;
    }

    public int getLinesOfCode() {

        return lnr != null ? lnr.getLineNumber() : -1;
    }

    public long getLexduration() {
        return lexduration;
    }

    public long getParseduration() {
        return parseduration;
    }

    public long getDuration() {
        return getLexduration() + getParseduration();
    }

    @Override
    public List<Interval> getPanic() {
        return panic;
    }

    public boolean isAbort() {
        return abort;
    }

    public void setAbort(boolean abort) {
        this.abort = abort;
    }
}
