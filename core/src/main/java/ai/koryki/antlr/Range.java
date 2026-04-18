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
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.Comparator;
import java.util.Objects;

public class Range implements Comparable<Range> {

    public Position getStart() {
        return start;
    }

    public Position getStop() {
        return stop;
    }

    private final Position start;

    private final Position stop;

    public Range(int startLine, int startPos, int stopLine, int stopPos) {
        this(new Position(startLine, startPos), new Position(stopLine, stopPos));
    }

    public Range(Position start, Position stop) {

        if (start.compareTo(stop) > 0) {
            throw new IllegalArgumentException("invalid range " + start + "-" + stop);
        }

        this.start = start;
        this.stop = stop;
    }

    public boolean overlaps(Range other) {

        Position otherStart = other.getStart();
        Position otherStop = other.getStop();

        if (start.getLine() == stop.getLine() && start.getLine() == otherStart.getLine() && otherStart.getLine() == otherStop.getLine()) {
            // all in one line
            boolean rightOuter = start.getPos() > otherStop.getPos();
            boolean leftOuter = stop.getPos() < otherStart.getPos();
            return !(rightOuter || leftOuter);
        } else if (start.getLine() == otherStart.getLine()) {
            // both start at same line
            boolean leftOuter = start.getLine() == stop.getLine() && stop.getPos() < otherStart.getPos();
            boolean rightOuter = otherStart.getLine() == otherStop.getLine() && otherStart.getPos() > stop.getPos();
            return !(rightOuter || leftOuter);
        } else if (stop.getLine() == otherStop.getLine()) {
            // both stop at same line
            boolean leftOuter = otherStart.getLine() == stop.getLine() && otherStart.getPos() > stop.getPos();
            boolean rightOuter = start.getLine() == stop.getLine() && otherStop.getPos() < start.getPos();
            return !(rightOuter || leftOuter);
        } else {
            // lines are different
            boolean upper = stop.getLine() < otherStart.getLine();
            boolean lower = start.getLine() > otherStop.getLine();
            return !(upper || lower);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, stop);
    }

    @Override
    public boolean equals(Object other) {

        if (other instanceof Range) {
            Range r = (Range) other;
            return start.equals(r.start)  && stop.equals(r.stop);
        }
        return false;
    }

    @Override
    public String toString() {

        return start + "-" + stop;
    }

    private static final Comparator<Range> comparator = Comparator.comparing(Range::getStart).thenComparing(Range::getStop);

    @Override
    public int compareTo(Range range) {
        return Objects.compare(this, range, comparator);
    }

    public static Range range(ParseTree pCtx) {

        if (pCtx instanceof ParserRuleContext) {
            return range((ParserRuleContext)pCtx);
        } else if (pCtx instanceof TerminalNode) {
            return range((TerminalNode)pCtx);
        } else if (pCtx == null) {
            throw new KorykiaiException("cant calc range NULL");
        } else {
            throw new KorykiaiException("cant calc range " + pCtx.getClass());
        }
    }

    public static Range range(ParserRuleContext pCtx) {
        Position start = Position.start(pCtx.getStart());
        Position stop = Position.stop(pCtx.getStop());
        return new Range(start, stop);
    }

    public static Range range(TerminalNode node) {
        Position position = Position.start(node.getSymbol());
        return new Range(position, position);
    }

    public static Range range(Token token) {
        Position position = Position.start(token);
        return new Range(position, position);
    }

}
