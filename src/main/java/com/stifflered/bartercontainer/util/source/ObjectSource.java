package com.stifflered.bartercontainer.util.source;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Minimal CRUD-like interface for a keyed object store.

 * Contract:
 *  - delete/save operate on full objects (not just keys) so implementations can derive the key.
 *  - load uses a key to fetch a single object or null if absent.
 *  - getAll reads all stored objects (potentially expensive).
 */
public interface ObjectSource<K, T> {

    /** Delete a stored object. Returns true if something was deleted. */
    boolean delete(@NotNull T type) throws Exception;

    /** Save (create or overwrite) the object to the underlying store. */
    boolean save(@NotNull T type) throws Exception;

    /** Load a single object by key, or null if it doesn't exist. */
    @Nullable
    T load(@NotNull K key) throws Exception;

    /** Load all stored objects. Use sparingly if the backing store is large. */
    List<T> getAll() throws Exception;

}
