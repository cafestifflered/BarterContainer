package com.stifflered.bartercontainer.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Utility methods for working with strings in the BarterContainer plugin.
 * <p>
 * Features include:
 * <ul>
 *     <li>Smart possessive string formatting ("James" → "James'").</li>
 *     <li>Duration formatting (milliseconds → human-readable text like "2 hours 5 minutes").</li>
 *     <li>Pluralization helpers for units of time and generic strings.</li>
 * </ul>
 */
public class StringUtil {

    /**
     * Array of supported {@link TimeUnit} values and their base string names.
     * Used for duration formatting.
     */
    @SuppressWarnings("unchecked")
    private static final Tuple<TimeUnit, String>[] TIME_UNITS = new Tuple[]{
            new Tuple<>(TimeUnit.DAYS, "day"),
            new Tuple<>(TimeUnit.HOURS, "hour"),
            new Tuple<>(TimeUnit.MINUTES, "minute"),
            new Tuple<>(TimeUnit.SECONDS, "second")
    };

    /**
     * Append the correct possessive suffix ("'s" or just "'") to a given word.
     *
     * @param possessor the base word/name (e.g., "James")
     * @return the word with a possessive suffix (e.g., "James'")
     */
    public static String smartPossessive(String possessor) {
        if (possessor.endsWith("s")) {
            return possessor + "'";
        }
        return possessor + "'s";
    }

    /**
     * Format a duration in milliseconds into a human-readable string.
     * Uses up to 4 units (days, hours, minutes, seconds).
     *
     * @param millis the duration in milliseconds
     * @return a formatted string like "1 hour 5 minutes"
     */
    public static String formatMilliDuration(long millis) {
        return formatMilliDuration(millis, false);
    }

    /**
     * Format a duration in milliseconds, optionally using short text (like "1h 5m").
     *
     * @param millis    the duration in milliseconds
     * @param shortText true = use abbreviations (h, m, s), false = full names
     * @return formatted duration string
     */
    public static String formatMilliDuration(long millis, boolean shortText) {
        return formatMilliDuration(millis, TIME_UNITS.length, shortText);
    }

    /**
     * Format a duration in milliseconds using a maximum number of units.
     *
     * @param millis   the duration in milliseconds
     * @param distance the maximum number of units to include
     * @return formatted duration string
     */
    public static String formatMilliDuration(long millis, int distance) {
        return formatMilliDuration(millis, distance, false);
    }

    /**
     * Format a duration in milliseconds using custom unit count and short text option.
     *
     * @param millis     the duration in milliseconds
     * @param distance   the max number of time units to display
     * @param shortText  whether to use abbreviated unit names (h, m, s)
     * @return formatted duration string
     */
    public static String formatMilliDuration(long millis, int distance, boolean shortText) {
        List<String> symbols = new ArrayList<>();
        int currentDistance = 0;

        for (Tuple<TimeUnit, String> unit : TIME_UNITS) {
            String text = unit.valueB;
            Function<Long, String> stringFunction = shortText
                    ? (num) -> text.substring(0, 1) // abbreviate to first letter
                    : (num) -> " " + plural(text, num); // "hour"/"hours"

            if (applyTimeUnit(symbols, millis, unit.valueA, stringFunction)) {
                currentDistance++;
            }
            if (currentDistance >= distance) {
                break;
            }
        }

        return String.join(" ", symbols);
    }

    /**
     * Helper to add a unit of time to the formatted list if its value > 0.
     */
    private static boolean applyTimeUnit(List<String> symbols, long millis, TimeUnit unit, Function<Long, String> nameFunc) {
        long amt = getTimeUnit(millis, unit);
        if (amt > 0) {
            symbols.add(amt + nameFunc.apply(amt));
            return true;
        }
        return false;
    }

    /**
     * Extract the value of a particular time unit from a millisecond duration.
     * <p>
     * Each unit is calculated relative to the next-largest unit to prevent overlap.
     * Example: 1 hour 90 seconds → "1 hour 30 seconds".
     *
     * @param millis duration in milliseconds
     * @param unit   the {@link TimeUnit} to calculate
     * @return the numeric value of that unit
     */
    public static long getTimeUnit(long millis, TimeUnit unit) {
        TimeUnit ms = TimeUnit.MILLISECONDS;

        return switch (unit) {
            case DAYS -> ms.toDays(millis);
            case HOURS -> ms.toHours(millis) - TimeUnit.DAYS.toHours(ms.toDays(millis));
            case MINUTES -> ms.toMinutes(millis) - TimeUnit.HOURS.toMinutes(ms.toHours(millis));
            case SECONDS -> ms.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(ms.toMinutes(millis));
            case MILLISECONDS -> ms.toMillis(millis) - TimeUnit.SECONDS.toMillis(ms.toSeconds(millis));
            case MICROSECONDS -> millis; // treated same as raw value
            case NANOSECONDS -> ms.toNanos(millis);
        };
    }

    /**
     * Pluralize a string based on count.
     *
     * @param string the base word (e.g., "item")
     * @param number the count
     * @return "item" if number == 1, otherwise "items"
     */
    public static String plural(String string, long number) {
        return number == 1 ? string : string + "s";
    }
}
