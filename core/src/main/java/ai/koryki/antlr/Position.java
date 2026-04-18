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


import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import java.util.Objects;

public class Position implements Comparable<Position> {
    private final int line;
    private final int pos;

    public int getLine() {

        return line;
    }

    public int getPos() {

        return pos;
    }

    public Position(int line, int pos) {
        if (line < 0) {
            throw new IllegalArgumentException("negative line " + line);
        }
        if (pos < 0) {
            throw new IllegalArgumentException("negative positionInLine " + pos);
        }
        if (line > Integer.MAX_VALUE / 2) {
            throw new IllegalArgumentException("max line " + line);
        }
        if (pos > Integer.MAX_VALUE / 2) {
            throw new IllegalArgumentException("max positionInLine " + pos);
        }
        this.line = line;
        this.pos = pos;
    }

    @Override
    public int hashCode() {

        return Objects.hash(line, pos);
    }

    @Override
    public boolean equals(Object other) {

        if (other instanceof Position) {
            Position p = (Position) other;
            return line == p.getLine() && pos == p.getPos();
        }
        return false;
    }

    @Override
    public String toString() {
        return line + ":" + pos;
    }

    @Override
    public int compareTo(Position other) {

        if (line != other.line) {
            return Integer.compare(line, other.line);
        } else {
            return Integer.compare(pos, other.pos);
        }
    }

    public static Position stop(Token token) {
        String text = token.getText();
        if (text == null || text.isEmpty()) {
            return new Position(token.getLine(), token.getCharPositionInLine());
        }
        int lastNewline = text.lastIndexOf('\n');
        if (lastNewline < 0) {
            return new Position(token.getLine(), token.getCharPositionInLine() + text.length());
        }
        int newlineCount = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') newlineCount++;
        }
        return new Position(token.getLine() + newlineCount, text.length() - lastNewline - 1);
    }

    public static Position start(Token token) {

        return new Position(token.getLine(), token.getCharPositionInLine());
    }

    public static Position start(ParserRuleContext pCtx) {

        return start(pCtx.getStart());
    }

}
