package com.stifflered.tcchallenge.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class StringUtil {

    @SuppressWarnings("unchecked")
    private static final Tuple<TimeUnit, String>[] TIME_UNITS = new Tuple[]{
            new Tuple<>(TimeUnit.DAYS, "day"),
            new Tuple<>(TimeUnit.HOURS, "hour"),
            new Tuple<>(TimeUnit.MINUTES, "minute"),
            new Tuple<>(TimeUnit.SECONDS, "second")
    };

    public static String smartPossessive(String possessor) {
        if (possessor.endsWith("s")) {
            return possessor + "'";
        }

        return possessor + "'s";
    }

    public static String formatMilliDuration(long millis) {
        return formatMilliDuration(millis, false);
    }

    public static String formatMilliDuration(long millis, boolean shortText) {
        return formatMilliDuration(millis, TIME_UNITS.length, shortText);
    }

    public static String formatMilliDuration(long millis, int distance) {
        return formatMilliDuration(millis, distance, false);
    }

    public static String formatMilliDuration(long millis, int distance, boolean shortText) {
        List<String> symbols = new ArrayList<>();
        int currentDistance = 0;
        for (Tuple<TimeUnit, String> unit : TIME_UNITS) {
            String text = unit.valueB;
            Function<Long, String> stringFunction = shortText ? (num) -> text.substring(0, 1) : (num) -> " " + plural(text, num);

            if (applyTimeUnit(symbols, millis, unit.valueA, stringFunction)) {
                currentDistance++;
            }
            if (currentDistance >= distance) {
                break;
            }
        }

        return String.join(" ", symbols);
    }

    private static boolean applyTimeUnit(List<String> symbols, long millis, TimeUnit unit, Function<Long, String> nameFunc) {
        long amt = getTimeUnit(millis, unit);
        if (amt > 0) {
            symbols.add(amt + nameFunc.apply(amt));
            return true;
        }

        return false;
    }

    public static long getTimeUnit(long millis, TimeUnit unit) {
        TimeUnit ms = TimeUnit.MILLISECONDS;

        return switch (unit) {
            case DAYS -> ms.toDays(millis);
            case HOURS -> ms.toHours(millis) - TimeUnit.DAYS.toHours(ms.toDays(millis));
            case MINUTES -> ms.toMinutes(millis) - TimeUnit.HOURS.toMinutes(ms.toHours(millis));
            case SECONDS -> ms.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(ms.toMinutes(millis));
            case MILLISECONDS -> ms.toMillis(millis) - TimeUnit.SECONDS.toMillis(ms.toSeconds(millis));
            case MICROSECONDS -> millis;
            case NANOSECONDS -> ms.toNanos(millis);
        };
    }

    public static String plural(String string, long number) {
        return number == 1 ? string : string + "s";
    }
}
