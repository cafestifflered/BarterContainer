package com.stifflered.bartercontainer.util;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import java.util.Locale;

public final class TimeUtil {
    private TimeUtil() {}

    // Volatile so we can swap it on config reload safely
    private static volatile DateTimeFormatter ABS_FMT = defaultFormatter();

    private static DateTimeFormatter buildFormatter(String pattern) {
        try {
            return DateTimeFormatter.ofPattern(pattern)
                    .withLocale(Locale.ROOT)
                    .withZone(ZoneOffset.UTC);
        } catch (IllegalArgumentException ex) {
            // Fallback if someone misconfigures the pattern in YAML
            return defaultFormatter();
        }
    }

    private static DateTimeFormatter defaultFormatter() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
                .withLocale(Locale.ROOT)
                .withZone(ZoneOffset.UTC);
    }

    /** Call this on plugin startup and whenever you reload config. */
    public static void reloadFromConfig(FileConfiguration cfg) {
        String pattern = cfg.getString("transactions.time_pattern", "yyyy-MM-dd HH:mm 'UTC'");
        ABS_FMT = buildFormatter(pattern);
    }

    /** Get the current absolute timestamp formatter (UTC). */
    public static DateTimeFormatter absoluteFormatter() {
        return ABS_FMT;
    }
}
