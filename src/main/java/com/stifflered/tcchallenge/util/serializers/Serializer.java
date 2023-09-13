package com.stifflered.tcchallenge.util.serializers;

public interface Serializer<F, T> {

    T serialize(F from);
}
