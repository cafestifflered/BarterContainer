package com.stifflered.bartercontainer.barter;

import com.stifflered.bartercontainer.store.BarterStoreKey;

import java.util.UUID;

/**
 * Simple implementation of the {@link BarterStoreKey} interface using a UUID as the unique identifier.

 * Usage:
 *  - Acts as the "primary key" object for barter stores across persistence, caches, and chunk indexing.
 *  - Wraps a UUID so it can be treated as a domain-level key instead of a bare primitive type.

 * Notes:
 *  - Declared as a Java record, which auto-generates equals(), hashCode(), toString(), and the uuid() accessor.
 *  - Implements BarterStoreKey.key() explicitly to return the same UUID (alias for uuid()).
 */
public record BarterStoreKeyImpl(UUID uuid) implements BarterStoreKey {

    /** Explicit implementation of the BarterStoreKey contract. */
    @Override
    public UUID key() {
        return this.uuid;
    }
}
