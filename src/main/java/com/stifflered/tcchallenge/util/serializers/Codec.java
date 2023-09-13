package com.stifflered.tcchallenge.util.serializers;

public interface Codec<F, T> {

    T encode(F from);

    F decode(T type);
}
