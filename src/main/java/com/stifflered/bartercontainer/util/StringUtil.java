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

    public static boolean fuzzyContains(String targetNorm, String queryNorm) {
        if (targetNorm.contains(queryNorm)) return true;

        String[] targetTokens = targetNorm.split(" ");
        String[] queryTokens  = queryNorm.split(" ");

        for (String q : queryTokens) {
            boolean matched = false;
            for (String t : targetTokens) {
                if (t.isEmpty()) continue;

                // 1) exact token match always OK
                if (t.equals(q)) { matched = true; break; }

                // 2) short queries (<=3) must match a whole token, not substring
                if (q.length() <= 3) continue;

                // 3) only allow targetToken to contain the query (not the reverse)
                if (t.length() >= 3 && t.contains(q)) { matched = true; break; }

                // 4) edit distance as a fallback for typos
                int d = damerauLevenshtein(t, q);
                int maxLen = Math.max(t.length(), q.length());
                int tol = Math.max(1, (int) Math.floor(maxLen * 0.34));
                if (d <= tol) { matched = true; break; }
            }
            if (!matched) return false;
        }
        return true;
    }

    public static int damerauLevenshtein(String a, String b) {
        int n = a.length(), m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) dp[i][0] = i;
        for (int j = 0; j <= m; j++) dp[0][j] = j;
        for (int i = 1; i <= n; i++) {
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                char cb = b.charAt(j - 1);
                int cost = (ca == cb) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
                if (i > 1 && j > 1 && ca == b.charAt(j - 2) && a.charAt(i - 2) == cb) {
                    dp[i][j] = Math.min(dp[i][j], dp[i - 2][j - 2] + 1);
                }
            }
        }
        return dp[n][m];
    }

    public static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> "";
        };
    }
}
