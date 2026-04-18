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

public class Interval {

    private int start;
    private int stop;
    private String msg;

    public Interval(int start, int stop, String msg) {

        if (start > stop) {
            throw new IllegalArgumentException("invalid interval: "+ start + ": " + stop);
        }
        this.start = start;
        this.stop = stop;
        this.msg = msg;
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
        return start + ":" + stop + " " + msg;
    }

}
