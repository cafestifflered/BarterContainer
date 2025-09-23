package com.stifflered.bartercontainer.util;

import java.util.List;
import java.util.Random;

/**
 * Utility wrapper around Java's {@link Random}, providing convenience methods
 * for generating random numbers, booleans, and selecting random elements from collections.
 * <p>
 * Uses a single shared {@link Random} instance to avoid unnecessary object creation.
 */
public class RandomUtil {

    /** Shared instance of {@link Random}. */
    private static final Random random = new Random();

    /**
     * @return a random {@code float} between {@code 0.0} (inclusive) and {@code 1.0} (exclusive).
     */
    public static float nextFloat() {
        return random.nextFloat();
    }

    /**
     * @return a random {@code int} within the full integer range.
     */
    public static int nextInt() {
        return random.nextInt();
    }

    /**
     * @param bound the exclusive upper bound (must be positive).
     * @return a random {@code int} between {@code 0} (inclusive) and {@code bound} (exclusive).
     */
    public static int nextInt(int bound) {
        return random.nextInt(bound);
    }

    /**
     * @return a normally distributed {@code double} value with mean {@code 0.0}
     * and standard deviation {@code 1.0}.
     */
    public static double nextGaussian() {
        return random.nextGaussian();
    }

    /**
     * @return a random {@code boolean}, {@code true} or {@code false} with equal probability.
     */
    public static boolean nextBoolean() {
        return random.nextBoolean();
    }

    /**
     * Picks a random element from a {@link List}.
     *
     * @param list the list to pick from
     * @param <T>  the type of elements in the list
     * @return a random element, or {@code null} if the list is empty
     */
    public static <T> T randomIndex(List<T> list) {
        if (list.isEmpty()) {
            return null;
        }
        return list.get(nextInt(list.size()));
    }

    /**
     * Picks a random element from an array.
     *
     * @param values the array to pick from
     * @param <T>    the type of elements in the array
     * @return a random element from the array
     * @throws ArrayIndexOutOfBoundsException if the array is empty
     */
    public static <T> T randomIndex(T[] values) {
        return values[nextInt(values.length)];
    }

    /**
     * @return the shared {@link Random} instance used by this utility.
     */
    public static Random getRandom() {
        return random;
    }
}
