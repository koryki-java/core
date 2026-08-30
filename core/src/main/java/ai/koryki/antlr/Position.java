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

/**
 * One point in the query text the user wrote: <b>line and column are both 1-based</b>, and a range's
 * stop is exclusive — the same convention an editor uses.
 *
 * <p>ANTLR does not hand it over that way. {@code Token.getLine()} counts from 1, but
 * {@code getCharPositionInLine()} counts from 0, and passing that mixture on made every column read
 * one character too far left. Measured on {@code to_number.kql}: {@code to_number} starts at line 5,
 * column 7, and the violation used to report {@code 5:6}. The conversion therefore happens once,
 * here at the boundary, in {@link #column(int)} — never at a call site, where it would be forgotten
 * in one of them.
 *
 * <p>The constructor rejects anything below 1 for the same reason: a 0 can only come from a raw
 * ANTLR value that skipped this class, and that is worth an exception rather than a position that is
 * quietly off by one. Code that needs a placeholder rather than a real position uses
 * {@code (1, 1)}.
 *
 * <p>Comparisons — {@link #compareTo}, {@link #equals}, {@code Range.overlaps} — are unaffected by
 * the convention: they only ever relate positions of the same origin, and a uniform shift leaves
 * their order untouched.
 */
public class Position implements Comparable<Position> {
    private final int line;
    private final int pos;

    /**
     * ANTLR's 0-based {@code charPositionInLine} as a 1-based column. The single place the offset
     * lives.
     */
    private static int column(int charPositionInLine) {
        return charPositionInLine + 1;
    }

    public int getLine() {

        return line;
    }

    public int getPos() {

        return pos;
    }

    public Position(int line, int pos) {
        if (line < 1) {
            throw new IllegalArgumentException("line is 1-based, got " + line);
        }
        if (pos < 1) {
            throw new IllegalArgumentException("column is 1-based, got " + pos
                    + " — a raw ANTLR charPositionInLine must go through Position.start/stop");
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

    /** One past the token's last character — exclusive, as an editor's end column is. */
    public static Position stop(Token token) {
        String text = token.getText();
        if (text == null || text.isEmpty()) {
            return new Position(token.getLine(), column(token.getCharPositionInLine()));
        }
        int lastNewline = text.lastIndexOf('\n');
        if (lastNewline < 0) {
            return new Position(token.getLine(), column(token.getCharPositionInLine() + text.length()));
        }
        int newlineCount = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') newlineCount++;
        }
        // A multi-line token ends on a later line, so the column restarts after the last newline.
        return new Position(token.getLine() + newlineCount, column(text.length() - lastNewline - 1));
    }

    public static Position start(Token token) {

        return new Position(token.getLine(), column(token.getCharPositionInLine()));
    }

    public static Position start(ParserRuleContext pCtx) {

        return start(pCtx.getStart());
    }

}
