package com.stifflered.tcchallenge.util;

import org.bukkit.Location;

public class Maths {

    public static double lerp(double min, double max, double percent) {
        return min + percent * (max - min);
    }

    public static float lerp(float min, float max, float percent) {
        return min + percent * (max - min);
    }

    // inclusive wrap
    public static int wrap(int value, int lower, int upper) {
        upper += 1;
        return Math.floorMod(value - lower, upper) + lower;
    }

    public static double percent(int number) {
        return number / 100D;
    }

    public static double reversePercent(int number) {
        return 1 - (number / 100D);
    }

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
