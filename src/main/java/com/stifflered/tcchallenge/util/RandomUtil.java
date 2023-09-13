package com.stifflered.tcchallenge.util;

import java.util.List;
import java.util.Random;

public class RandomUtil {

    private static final Random random = new Random();

    public static float nextFloat() {
        return random.nextFloat();
    }

    public static int nextInt() {
        return random.nextInt();
    }

    public static int nextInt(int bound) {
        return random.nextInt(bound);
    }

    public static double nextGaussian() {
        return random.nextGaussian();
    }

    public static boolean nextBoolean() {
        return random.nextBoolean();
    }

    public static <T> T randomIndex(List<T> list) {
        if (list.isEmpty()) {
            return null;
        }

        return list.get(nextInt(list.size()));
    }

    public static <T> T randomIndex(T[] values) {
        return values[nextInt(values.length)];
    }

    public static Random getRandom() {
        return random;
    }

}
