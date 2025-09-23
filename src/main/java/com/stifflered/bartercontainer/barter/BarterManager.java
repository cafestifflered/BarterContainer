package com.stifflered.bartercontainer.barter;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.barter.serializers.InGameBarterSerializer;
import com.stifflered.bartercontainer.event.CreateBarterContainer;
import com.stifflered.bartercontainer.event.RemoveBarterContainer;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.store.BarterStoreImpl;
import com.stifflered.bartercontainer.store.BarterStoreKey;
import com.stifflered.bartercontainer.util.Messages;
import com.stifflered.bartercontainer.util.source.Sources;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Central coordinator for BarterStore lifecycle:
 *  - Creates new stores and writes identity into block PDC via serializer.
 *  - Keeps an in-memory cache of loaded BarterStore instances (by BarterStoreKey).
 *  - Loads/stores data with the backing storage provider (Sources.BARTER_STORAGE).
 *  - Emits Bukkit events on create/remove.

 * Threading & I/O:
 *  - Uses async tasks (BukkitRunnable#runTaskAsynchronously) for potentially slow save/load/delete I/O.
 *  - In-memory map is a ConcurrentHashMap to allow concurrent reader/writer access.

 * Separation of concerns:
 *  - Physical persistence is delegated to Sources.BARTER_STORAGE.
 *  - Key extraction/encoding in block PDC is handled by InGameBarterSerializer.
 */
public class BarterManager {

    /** Global singleton used throughout the plugin (simple eager initialization). */
    public static final BarterManager INSTANCE = new BarterManager();

    /** Encodes/decodes BarterStore identity into a block's PersistentDataContainer. */
    private final InGameBarterSerializer serializer = new InGameBarterSerializer();

    /** Runtime cache of loaded stores keyed by their logical identity. */
    private final Map<BarterStoreKey, BarterStore> storage = new ConcurrentHashMap<>();

    /**
     * Creates a new store instance, persists its key into the block PDC, caches it, and fires a creation event.
     * @param barterStore the fully constructed store (domain object with key)
     * @param persistentDataContainer target block PDC to write key data into
     * @param chunk the chunk containing the store (passed to event)

     * Flow:
     *  1) serializer.writeBarterStoreKey(...) -> block PDC now references this store.
     *  2) storage.put(...) -> runtime cache includes it.
     *  3) new CreateBarterContainer(...).callEvent() -> listeners can react (e.g., index updates).
     */
    public void createNewStore(BarterStoreImpl barterStore, PersistentDataContainer persistentDataContainer, Chunk chunk) {
        this.serializer.writeBarterStoreKey(persistentDataContainer, barterStore);
        this.storage.put(barterStore.getKey(), barterStore);
        new CreateBarterContainer(barterStore, chunk).callEvent();
    }

    /**
     * Removes a store given its block location.
     * Steps:
     *  - Look up the BarterStoreKey from the block's PDC.
     *  - Remove from runtime cache.
     *  - Fire RemoveBarterContainer event.
     *  - Delete persisted data asynchronously (swallows any exception).
     *
     * @return true if a store was found and removed; false if no store key at location.
     */
    public boolean removeBarterContainer(Location location) {
        Optional<BarterStore> value = this.keyAtLocation(location)
                .map(storage::remove);

        if (value.isPresent()) {
            new RemoveBarterContainer(value.get(), location.getChunk()).callEvent();
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        Sources.BARTER_STORAGE.delete(value.get());
                    } catch (Exception ignored) {
                        // Intentionally ignored: deletion failures are not surfaced to players.
                        // Consider logging in the future if diagnostics are needed.
                    }
                }
            }.runTaskAsynchronously(BarterContainer.INSTANCE);

            return true;
        }

        return false;
    }

    /**
     * Loads a store by key from persistent storage and, if the condition passes, puts it into the runtime cache.
     *
     * @param barterStoreKey identity to load
     * @param conditionCheck predicate executed against the loaded store (e.g., "chunk still loaded?")
     * @return true if the store existed on disk (and was cached when the condition passed);
     *         false when the store is missing on disk (caller should unlink/prune the stale reference).

     * Notes:
     *  - No longer throws when the storage record is missing. We log and return false instead.
     *  - Callers can safely invoke this from async threads; the method itself is synchronous.
     *  - Underlying storage errors (I/O, serialization, etc.) may still throw.
     */
    public boolean loadAndCacheContainer(BarterStoreKey barterStoreKey,
                                         Predicate<BarterStore> conditionCheck) throws Exception {
        BarterStore store = Sources.BARTER_STORAGE.load(barterStoreKey);

        if (store == null) {
            // Orphan: the world/chunk references a store that no longer exists on disk.
            BarterContainer.INSTANCE.getLogger().warning(
                    Messages.fmt("barter.manager.missing_storage", "key", String.valueOf(barterStoreKey))
            );
            return false;
        }

        if (conditionCheck.test(store)) {
            this.storage.put(barterStoreKey, store);
        }
        return true;
    }

    /**
     * Persists and evicts a set of stores (by UUID) during chunk unload.
     * For each UUID:
     *  - Remove from cache (if present).
     *  - Schedule async save of the removed store.

     * Notes:
     *  - If a UUID has no cached store, it is skipped (already unloaded).
     *  - Saves are fire-and-forget; exceptions are printed but not retried.
     */
    public void saveContainersAndUnload(List<UUID> uuids) {
        for (UUID uuid : uuids) {
            BarterStore store = storage.remove(new BarterStoreKeyImpl(uuid));
            if (store != null) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            Sources.BARTER_STORAGE.save(store);
                        } catch (Exception e) {
                            BarterContainer.INSTANCE.getLogger().warning(
                                    Messages.fmt("barter.manager.save_failed_title", "key", uuid.toString())
                            );
                            BarterContainer.INSTANCE.getLogger().warning(
                                    Messages.fmt("barter.manager.save_failed_detail", "detail", String.valueOf(e.getMessage()))
                            );
                            e.printStackTrace();
                        }
                    }
                }.runTaskAsynchronously(BarterContainer.INSTANCE);
            }
        }
    }

    /**
     * Removes a store from the runtime cache without touching persistence.
     * Intended for cases where the container is deleted and its index has been updated elsewhere.
     */
    public void expungeContainer(UUID uuid) {
        this.storage.remove(new BarterStoreKeyImpl(uuid));
    }

    /**
     * New async convenience: resolves a store at a location, loading it if needed, and completes when ready.

     * Behavior:
     *  - If there is no PDC key at the location → completes with Optional.empty().
     *  - If already cached → completes immediately with the cached instance.
     *  - If not cached → performs async load via {@link #loadAndCacheContainer}, then completes with the instance
     *    (or Optional.empty() if missing on disk / failed to load).

     * Threading:
     *  - The I/O happens on a Bukkit async task.
     *  - The returned future completes on that async thread; callers that need to touch Bukkit objects
     *    must hop back to the main thread themselves.
     */
    public CompletableFuture<Optional<BarterStore>> getOrLoadAsync(Location location) {
        Optional<BarterStoreKey> keyOpt = this.keyAtLocation(location);
        if (keyOpt.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        BarterStoreKey key = keyOpt.get();
        BarterStore cached = this.storage.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }

        CompletableFuture<Optional<BarterStore>> fut = new CompletableFuture<>();
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    boolean ok = loadAndCacheContainer(key, (store) -> true);
                    if (!ok) {
                        fut.complete(Optional.empty()); // missing on disk (already logged)
                    } else {
                        fut.complete(Optional.ofNullable(storage.get(key)));
                    }
                } catch (Exception e) {
                    BarterContainer.INSTANCE.getLogger().warning(
                            Messages.fmt("barter.manager.load_failed_title", "key", String.valueOf(key))
                    );
                    BarterContainer.INSTANCE.getLogger().warning(
                            Messages.fmt("barter.manager.load_failed_detail", "detail", String.valueOf(e.getMessage()))
                    );
                    e.printStackTrace();
                    fut.complete(Optional.empty());
                }
            }
        }.runTaskAsynchronously(BarterContainer.INSTANCE);

        return fut;
    }

    /**
     * Gets a cached store at the given block location (no loading).
     * Returns empty if no key is present or if the store is not in the runtime cache.
     */
    public Optional<BarterStore> getBarter(Location location) {
        return this.keyAtLocation(location)
                .map(this.storage::get);
    }

    /**
     * Attempts to extract the BarterStoreKey from the PDC of the block at the given location.
     * Uses getState(false) to avoid forcing a tile entity snapshot update.
     */
    private Optional<BarterStoreKey> keyAtLocation(Location location) {
        if (location.getBlock().getState(false) instanceof TileState tileState) {
            PersistentDataContainer container = tileState.getPersistentDataContainer();
            return this.serializer.getBarterStoreKey(container);
        }

        return Optional.empty();
    }

    /**
     * Retrieves all stores from persistent storage (NOT the in-memory cache).
     * Returns an empty list if an exception occurs.

     * Note:
     *  - This may be I/O heavy depending on the storage backend; use sparingly or off the main thread.
     */
    public List<BarterStore> getAll() throws RuntimeException {
        try {
            return Sources.BARTER_STORAGE.getAll();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    /**
     * Saves all currently cached stores (no batching) via the storage backend.
     * Exceptions are printed per store and do not halt the loop.

     * Called on plugin disable to flush runtime state.
     */
    public void saveAll() {
        storage.values().forEach(barterStore -> {
            try {
                Sources.BARTER_STORAGE.save(barterStore);
            } catch (Exception e) {
                BarterContainer.INSTANCE.getLogger().warning(
                        Messages.fmt("barter.manager.save_failed_title", "key", String.valueOf(barterStore.getKey().key()))
                );
                BarterContainer.INSTANCE.getLogger().warning(
                        Messages.fmt("barter.manager.save_failed_detail", "detail", String.valueOf(e.getMessage()))
                );
                e.printStackTrace();
            }
        });
    }

    /**
     * Saves a single store via the storage backend.
     * Caller decides threading; this method is synchronous as written.
     */
    public void save(BarterStore store) {
        try {
            Sources.BARTER_STORAGE.save(store);
        } catch (Exception e) {
            BarterContainer.INSTANCE.getLogger().warning(
                    Messages.fmt("barter.manager.save_failed_title", "key", String.valueOf(store.getKey().key()))
            );
            BarterContainer.INSTANCE.getLogger().warning(
                    Messages.fmt("barter.manager.save_failed_detail", "detail", String.valueOf(e.getMessage()))
            );
            e.printStackTrace();
        }
    }

    /**
     * Asynchronously resolves all shops owned by a player based on persistent storage enumeration.
     * Implementation:
     *  - supplyAsync: runs on a generic ForkJoinPool (not the Bukkit scheduler).
     *  - getAll(): pulls from persistent storage; if that is I/O bound, this avoids blocking the main thread.
     *  - Filters by player UUID against each store's PlayerProfile.

     * Note:
     *  - Because getAll() itself catches exceptions and returns an empty list, failures become silent empties.
     */
    public CompletableFuture<List<BarterStore>> getOwnedShops(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            List<BarterStore> stores = new ArrayList<>();
            for (BarterStore store : BarterManager.INSTANCE.getAll()) {
                // If they are the owner
                if (player.getUniqueId().equals(store.getPlayerProfile().getId())) {
                    stores.add(store);
                }
            }

            return stores;
        });
    }
}
