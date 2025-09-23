package com.stifflered.bartercontainer.util;

import org.bukkit.Location;

/**
 * Utility math functions used throughout the plugin.
 * Provides helpers for linear interpolation, percent conversions,
 * modular wrapping, and working with {@link Location} values.
 */
public class Maths {

    /**
     * Linear interpolation between two doubles.
     *
     * @param min     starting value
     * @param max     ending value
     * @param percent fraction between 0.0–1.0 (0 → min, 1 → max)
     * @return interpolated value
     */
    public static double lerp(double min, double max, double percent) {
        return min + percent * (max - min);
    }

    /**
     * Linear interpolation between two floats.
     *
     * @param min     starting value
     * @param max     ending value
     * @param percent fraction between 0.0–1.0 (0 → min, 1 → max)
     * @return interpolated value
     */
    public static float lerp(float min, float max, float percent) {
        return min + percent * (max - min);
    }

    /**
     * Wrap an integer within a given inclusive range.
     * <p>
     * Example: wrap(7, 0, 5) → 1
     *
     * @param value the number to wrap
     * @param lower inclusive lower bound
     * @param upper inclusive upper bound
     * @return wrapped value in [lower, upper]
     */
    public static int wrap(int value, int lower, int upper) {
        upper += 1; // make range inclusive
        return Math.floorMod(value - lower, upper) + lower;
    }

    /**
     * Convert an integer percentage to decimal.
     *
     * @param number e.g., 25 → 0.25
     * @return percentage as decimal fraction
     */
    public static double percent(int number) {
        return number / 100D;
    }

    /**
     * Convert an integer percentage to its complement (1 - percent).
     *
     * @param number e.g., 25 → 0.75
     * @return reversed percentage as decimal fraction
     */
    public static double reversePercent(int number) {
        return 1 - (number / 100D);
    }

    /**
     * Linearly interpolate between two {@link Location}s.
     * Interpolates position (x,y,z) and rotation (yaw,pitch).
     * <p>
     * Assumes both locations are in the same world.
     *
     * @param min     starting location
     * @param max     ending location
     * @param percent fraction between 0.0–1.0
     * @return new interpolated location
     */
    public static Location lerp(Location min, Location max, float percent) {
        return new Location(
                min.getWorld(),
                Maths.lerp(min.getX(), max.getX(), percent),
                Maths.lerp(min.getY(), max.getY(), percent),
                Maths.lerp(min.getZ(), max.getZ(), percent),
                Maths.lerp(min.getYaw(), max.getYaw(), percent),
                Maths.lerp(min.getPitch(), max.getPitch(), percent)
        );
    }
}
