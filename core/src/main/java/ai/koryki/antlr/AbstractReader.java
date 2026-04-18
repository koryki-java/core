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
package ai.koryki.antlr;

import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public abstract class AbstractReader<L extends Lexer, P extends Parser, C extends ParseTree> {

    public abstract L getLexer() ;
    public abstract P getParser();
    public abstract BufferedTokenStream getTokens();
    public abstract List<Interval> getPanic();
    public abstract C getCtx();

    public int getTokenCount() {
        return getTokens() != null ? getTokens().size() : -1;
    }

    public String getComment(ParseTree node) {
        List<Token> hiddenTokens =
                getTokens().getHiddenTokensToLeft(node.getSourceInterval().a);

        StringBuilder b = new StringBuilder();
        if (hiddenTokens != null) {

            for (Token t : hiddenTokens) {

                String c = trimComment(t.getText());
                if (c.isEmpty()) {
                    continue;
                }
                if (b.length() > 0) {
                    b.append(System.lineSeparator());
                }
                b.append(c);
            }
        }
        return b.length() > 0 ? b.toString() : null;
    }

    private String trimComment(String c) {

        if (c.startsWith("/*") && c.endsWith("*/")) {
            c = c.substring(2, c.length() - 2).trim();
            return c;
        } else if (c.startsWith("//")) {
            c = c.trim();
            return c.substring(2);
        } else if (c.trim().isEmpty()) {
            return "";
        } else {
            return c.trim();
        }
    }

    public static String readResource(String resource) {
        try {
            return readStream(AbstractReader.class.getResourceAsStream(resource));
        } catch (IOException e) {
            throw new KorykiaiException(e);
        }
    }

    public static String readStream(InputStream in) throws IOException {
        return readStream(in, StandardCharsets.UTF_8);
    }

    public static String readStream(InputStream in, Charset cs) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        in.transferTo(result);
        return result.toString(cs);
    }
}

