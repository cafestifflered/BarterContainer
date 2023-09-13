package com.stifflered.tcchallenge.tree;

import java.util.Objects;
import java.util.UUID;

public class TreeKey {

    private final UUID key;

    public TreeKey(UUID key) {
        this.key = key;
    }

    public UUID getKey() {
        return this.key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;
        TreeKey key1 = (TreeKey) o;
        return Objects.equals(this.key, key1.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.key);
    }

    @Override
    public String toString() {
        return "TreeKey{" +
                "key=" + this.key +
                '}';
    }
}
