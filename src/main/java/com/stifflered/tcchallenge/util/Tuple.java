package com.stifflered.tcchallenge.util;

public class Tuple<A, B> {

    public A valueA;
    public B valueB;

    public Tuple() {
        this.valueA = null;
        this.valueB = null;
    }

    public Tuple(A valueA, B valueB) {
        this.valueA = valueA;
        this.valueB = valueB;
    }
}
