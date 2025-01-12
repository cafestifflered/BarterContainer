package com.stifflered.bartercontainer.util.source.codec;

public interface Codec<F, T> {

    T encode(F from);

    F decode(T type);
}
