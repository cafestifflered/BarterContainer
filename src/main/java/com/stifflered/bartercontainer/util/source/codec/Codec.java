package com.stifflered.bartercontainer.util.source.codec;

/**
 * Simple bidirectional converter between types F (from) and T (to).

 * Intended use:
 *  - Serialization: encode(domain) -> storage representation (e.g., JSON).
 *  - Deserialization: decode(storage) -> domain object.

 * Implementations should be pure and side effect free.
 */
public interface Codec<F, T> {

    /** Convert from domain object to storage/transfer type. */
    T encode(F from);

    /** Convert from storage/transfer type back to domain object. */
    F decode(T type);
}
