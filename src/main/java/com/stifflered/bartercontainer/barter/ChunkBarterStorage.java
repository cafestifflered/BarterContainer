package com.stifflered.bartercontainer.barter;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.Messages;
import com.stifflered.bartercontainer.util.TagUtil;

import org.apache.logging.log4j.util.Strings;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Maintains a mapping between a {@link Chunk} and the set of Barter container IDs (UUIDs) inside it.

 * Persistence Strategy:
 *  - Uses the chunk's PersistentDataContainer (PDC) to store a comma-separated list of UUIDs under the key BARTER_KEYS.
 *  - This is lightweight and survives server restarts without owning a separate data file per chunk.

 * Runtime Cache Coordination:
 *  - Delegates to {@link BarterManager} for actual container load/unload/caching.
 *  - On chunk load, asynchronously loads containers for the recorded UUIDs (guarded by a "chunk still loaded" predicate).
 *  - On chunk unload, flushes/saves containers and evicts them from cache.

 * Threading:
 *  - handleLoad() spins an async task to avoid blocking the main thread during I/O-heavy loads.
 *  - A chunk-loaded predicate is passed into the manager to prevent loading after the chunk is unloaded mid-task.

 * Data Integrity Notes:
 *  - PDC value is a single STRING of comma-separated UUIDs. If malformed, read(...) can throw in UUID.fromString.
 *  - Duplicate adds are not currently de-duplicated (ArrayList used). Upstream should ensure uniqueness.
 */
public class ChunkBarterStorage {

    /** Namespaced key for storing the comma-separated UUID list in a chunk's PDC. */
    private static final NamespacedKey BARTER_KEYS = TagUtil.of("barter_keys");

    /** Central coordinator for loading, caching, and saving barter containers. */
    private final BarterManager barterManager;

    /** Inject the manager responsible for persistence and cache lifecycle. */
    public ChunkBarterStorage(BarterManager barterManager) {
        this.barterManager = barterManager;
    }

    /**
     * Register a newly created barter store with the chunk that contains it.
     * Effect:
     *  - Reads existing UUID list from PDC, appends the new store ID, writes back.
     *  - Does not attempt to load/store here; purely index maintenance.

     * Caveat:
     *  - No duplicate prevention here—callers should ensure a store isn't added twice.
     */
    public void handleAdd(Chunk chunk, BarterStore store) {
        List<UUID> entries = read(chunk);
        if (entries == null) {
            entries = new ArrayList<>();
        }
        entries.add(store.getKey().key());

        write(entries, chunk);
    }

    /**
     * Unregister a store from its chunk index and expunge it from the manager cache.
     * Steps:
     *  - Remove the UUID from the chunk's PDC list.
     *  - Notify manager to drop in-memory state for that container ID.
     */
    public void handleRemove(Chunk chunk, BarterStore removed) {
        UUID key = removed.getKey().key();
        List<UUID> entries = read(chunk);
        if (entries == null) {
            entries = new ArrayList<>();
        }
        entries.remove(key);

        write(entries, chunk);
        this.barterManager.expungeContainer(key);
    }

    /**
     * Remove a single UUID from the chunk's barter index and persist the change.
     * Safe to call even if the UUID is not present. If the last entry is removed, the PDC key is removed entirely.

     * IMPORTANT: Must be called on the main server thread because it writes to Bukkit PDC.
     */
    public void unlink(Chunk chunk, UUID staleId) {
        List<UUID> entries = read(chunk);
        if (entries == null || entries.isEmpty()) {
            return; // nothing to do
        }
        boolean changed = entries.remove(staleId);
        if (!changed) {
            return; // nothing to persist
        }

        // If empty after removal, delete the key from PDC to keep data tidy
        if (entries.isEmpty()) {
            chunk.getPersistentDataContainer().remove(BARTER_KEYS);
        } else {
            write(entries, chunk);
        }

        // Also ensure the manager isn't holding a zombie reference in its cache
        this.barterManager.expungeContainer(staleId);
    }

    /**
     * Invoked when a chunk is (re)loaded.
     * Behavior:
     *  - Reads the UUID list and, if non-empty, schedules an async task to load each container via the manager.
     *  - Before each load, checks that the chunk is still loaded to avoid racing with unloads.

     * Error Handling:
     *  - If a UUID points to missing storage on disk, we log once and **prune the stale UUID** from the chunk PDC.
     *  - Other exceptions are logged, and we continue with the rest of the entries.
     */
    public void handleLoad(Chunk chunk) {
        List<UUID> entries = read(chunk);
        if (entries == null || entries.isEmpty()) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : entries) {
                    if (!chunk.isLoaded()) {
                        break; // abort early if chunk unloaded mid-iteration
                    }

                    try {
                        // Ask manager to load. Returns false if backing storage is missing (no exception).
                        boolean loaded = barterManager.loadAndCacheContainer(
                                new BarterStoreKeyImpl(uuid),
                                (store) -> chunk.isLoaded() // ensure chunk still loaded when caching
                        );

                        if (!loaded) {
                            // Missing on disk: log once and prune the stale reference on the main thread.
                            BarterContainer.INSTANCE.getLogger().warning(
                                    Messages.fmt(
                                            "barter.chunk.orphan_pruned",
                                            "uuid", uuid.toString(),
                                            "chunk_x", String.valueOf(chunk.getX()),
                                            "chunk_z", String.valueOf(chunk.getZ())
                                    )
                            );
                            Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> unlink(chunk, uuid));
                        }

                    } catch (Exception e) {
                        BarterContainer.INSTANCE.getLogger().warning(
                                Messages.fmt("barter.chunk.load_failed_title", "uuid", uuid.toString())
                        );
                        BarterContainer.INSTANCE.getLogger().warning(
                                Messages.fmt("barter.chunk.load_failed_detail", "detail", String.valueOf(e.getMessage()))
                        );
                        e.printStackTrace();
                    }
                }
            }
        }.runTaskAsynchronously(BarterContainer.INSTANCE);
    }

    /**
     * Invoked when a chunk is about to be unloaded.
     * Behavior:
     *  - Reads the UUID index and asks the manager to persist and unload those containers.
     *  - Offloads save logic to the manager, which may perform sync/async I/O as designed.
     */
    public void handleUnload(Chunk unloaded) {
        List<UUID> entries = read(unloaded);
        if (entries == null || entries.isEmpty()) {
            return;
        }

        barterManager.saveContainersAndUnload(entries);
    }

    /**
     * Read the comma-separated UUID index from the chunk's PDC.

     * Returns:
     *  - null if the key is missing/blank (caller treats as empty).
     *  - Otherwise, a List of parsed UUIDs.

     * Pitfalls:
     *  - Malformed UUID strings will throw IllegalArgumentException from UUID.fromString.
     */
    @Nullable
    private static List<UUID> read(Chunk chunk) {
        String values = chunk.getPersistentDataContainer().get(BARTER_KEYS, PersistentDataType.STRING);
        if (values == null || values.isBlank()) {
            return null;
        }

        List<UUID> uuids = new ArrayList<>();
        for (String key : values.split(",")) {
            uuids.add(UUID.fromString(key));
        }

        return uuids;
    }

    /**
     * Serialize and store the UUID index into the chunk's PDC as a single comma-separated STRING.

     * Notes:
     *  - Order is preserved as given in 'values'.
     *  - If 'values' is empty, we REMOVE the key from PDC to avoid leaving blank markers behind.
     *  - Uses Log4j Strings.join for simple CSV emission (no escaping; UUIDs don't contain commas).
     */
    private void write(List<UUID> values, Chunk chunk) {
        if (values == null || values.isEmpty()) {
            chunk.getPersistentDataContainer().remove(BARTER_KEYS);
            return;
        }

        List<String> uuids = new ArrayList<>();
        for (UUID key : values) {
            uuids.add(key.toString());
        }
        chunk.getPersistentDataContainer().set(BARTER_KEYS, PersistentDataType.STRING, Strings.join(uuids, ','));
    }
}
