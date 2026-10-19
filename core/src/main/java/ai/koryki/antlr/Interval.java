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

import java.util.List;

/**
 * A stretch of tokens the parser could not make sense of, with the message ANTLR gave for it.
 *
 * <p>{@code start} and {@code stop} are <b>token indices</b>, which is what highlighting needs and
 * what nobody reading a message can use. {@link #getLine()} and {@link #getCol()} therefore carry
 * the position of the offending token in the text the user wrote, 1-based like {@link Position},
 * and {@link #toString()} leads with them when they are known.
 */
public class Interval {

    private int start;
    private int stop;
    private String msg;
    private int line;
    private int col;
    private List<String> expected = List.of();
    private List<String> didYouMean = List.of();

    public Interval(int start, int stop, String msg) {

        if (start > stop) {
            throw new IllegalArgumentException("invalid interval: " + start + ": " + stop);
        }
        this.start = start;
        this.stop = stop;
        this.msg = msg;
    }

    /**
     * An interval that also knows where the offending token is.
     *
     * @param line 1-based line of the offending token
     * @param col 1-based column of the offending token
     * @param expected the keywords and punctuation the grammar would have accepted there
     * @param didYouMean keywords that were probably meant
     */
    public Interval(
            int start,
            int stop,
            String msg,
            int line,
            int col,
            List<String> expected,
            List<String> didYouMean) {
        this(start, stop, msg);
        this.line = line;
        this.col = col;
        this.expected = List.copyOf(expected);
        this.didYouMean = List.copyOf(didYouMean);
    }

    public boolean contains(int i) {
        return start <= i && i <= stop;
    }

    @Deprecated
    public boolean start(int i) {
        return isStart(i);
    }

    public boolean isStart(int i) {
        return start == i;
    }

    @Deprecated
    public boolean stop(int i) {
        return isStop(i);
    }

    public boolean isStop(int i) {
        return stop == i;
    }

    public int getStart() {
        return start;
    }

    public int getStop() {
        return stop;
    }

    public String getMsg() {
        return msg;
    }

    /** 1-based line of the offending token, or 0 when unknown. */
    public int getLine() {
        return line;
    }

    /** 1-based column of the offending token, or 0 when unknown. */
    public int getCol() {
        return col;
    }

    public boolean hasPosition() {
        return line > 0 && col > 0;
    }

    /** Keywords and punctuation that would have been accepted; empty when none can be named. */
    public List<String> getExpected() {
        return expected;
    }

    /** Keywords that were probably meant; empty when nothing is close. */
    public List<String> getDidYouMean() {
        return didYouMean;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof Interval)) {
            return false;
        }

        Interval interval = (Interval) o;
        return start == interval.start && stop == interval.stop;
    }

    @Override
    public int hashCode() {
        int result = start;
        result = 31 * result + stop;
        return result;
    }

    @Override
    public String toString() {
        if (hasPosition()) {
            return "line " + line + ":" + col + " " + msg;
        }
        return start + ":" + stop + " " + msg;
    }
}
