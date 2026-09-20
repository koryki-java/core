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
package ai.koryki.iql.time;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.iql.query.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Time {

    private static final Pattern COMPONENT = Pattern.compile("(\\d+)(ms|min|mo|s|h|d|w|q|y)");

    public static Duration duration(String text) {
        Matcher m = COMPONENT.matcher(text);
        List<Duration.Component> components = new ArrayList<>();
        while (m.find()) {
            int value = Integer.parseInt(m.group(1));
            Duration.Unit unit =
                    switch (m.group(2)) {
                        case "ms" -> Duration.Unit.MILLISECOND;
                        case "s" -> Duration.Unit.SECOND;
                        case "min" -> Duration.Unit.MINUTE;
                        case "h" -> Duration.Unit.HOUR;
                        case "d" -> Duration.Unit.DAY;
                        case "w" -> Duration.Unit.WEEK;
                        case "mo" -> Duration.Unit.MONTH;
                        case "q" -> Duration.Unit.QUARTAL;
                        case "y" -> Duration.Unit.YEAR;
                        default ->
                                throw new KorykiaiException("Invalid duration unit: " + m.group(2));
                    };
            components.add(new Duration.Component(value, unit));
        }
        if (components.isEmpty()) throw new KorykiaiException("Invalid duration: " + text);
        return new Duration(components);
    }
}
