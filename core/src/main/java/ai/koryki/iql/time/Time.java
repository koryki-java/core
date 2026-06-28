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
            Duration.Unit unit = switch (m.group(2)) {
                case "ms"  -> Duration.Unit.MILLISECOND;
                case "s"   -> Duration.Unit.SECOND;
                case "min" -> Duration.Unit.MINUTE;
                case "h"   -> Duration.Unit.HOUR;
                case "d"   -> Duration.Unit.DAY;
                case "w"   -> Duration.Unit.WEEK;
                case "mo"  -> Duration.Unit.MONTH;
                case "q"   -> Duration.Unit.QUARTAL;
                case "y"   -> Duration.Unit.YEAR;
                default    -> throw new KorykiaiException("Invalid duration unit: " + m.group(2));
            };
            components.add(new Duration.Component(value, unit));
        }
        if (components.isEmpty()) throw new KorykiaiException("Invalid duration: " + text);
        return new Duration(components);
    }
}
