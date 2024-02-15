package com.stifflered.bartercontainer.barter;

import com.stifflered.bartercontainer.store.BarterStoreKey;

import java.util.UUID;

public record BarterStoreKeyImpl(UUID uuid) implements BarterStoreKey {

    @Override
    public UUID key() {
        return this.uuid;
    }
}
