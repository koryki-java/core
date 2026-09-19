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
package ai.koryki.databases.cases;

import ai.koryki.jdbc.ColumnInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class StableFormatInfo implements ColumnInfo {

    private String header;

    @Override
     public String toString(Object o) {

        if (o instanceof Number) {
            return formatNumber((Number) o);
        }

        return o != null ? o.toString() : "";
    }

    public static String formatNumber(Number number) {

        BigDecimal bd = (number instanceof BigDecimal)
                ? (BigDecimal) number
                : new BigDecimal(number.toString());
        return bd.setScale(1, RoundingMode.HALF_DOWN).toString();
    }


    public String getHeader() {
        return header;
    }

    @Override
    public void setHeader(String header) {
        this.header = header;
    }

    @Override
    public String toString() {
        return getHeader();
    }
}
