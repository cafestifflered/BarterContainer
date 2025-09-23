package com.stifflered.bartercontainer.util;

/**
 * Simple generic container class to hold a pair of values.
 * <p>
 * Often referred to as a "tuple" or "pair," this class allows
 * two objects of potentially different types to be grouped together.
 *
 * @param <A> the type of the first value
 * @param <B> the type of the second value
 */
public class Tuple<A, B> {

    /** The first value in the tuple. */
    public A valueA;

    /** The second value in the tuple. */
    public B valueB;

    /**
     * Default constructor.
     * Initializes both values to {@code null}.
     */
    public Tuple() {
        this.valueA = null;
        this.valueB = null;
    }

    /**
     * Constructs a tuple with the given values.
     *
     * @param valueA the first value
     * @param valueB the second value
     */
    public Tuple(A valueA, B valueB) {
        this.valueA = valueA;
        this.valueB = valueB;
    }
}
