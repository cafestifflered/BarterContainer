package com.stifflered.bartercontainer.barter;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.TagUtil;
import org.apache.logging.log4j.util.Strings;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChunkBarterStorage {

    private static final NamespacedKey BARTER_KEYS = TagUtil.of("barter_keys");

    private final BarterManager barterManager;

    public ChunkBarterStorage(BarterManager barterManager) {
        this.barterManager = barterManager;
    }

    public void handleAdd(Chunk chunk, BarterStore store) {
        List<UUID> entries = read(chunk);
        if (entries == null) {
            entries = new ArrayList<>();
        }
        entries.add(store.getKey().key());


        write(entries, chunk);
    }

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

    public void handleLoad(Chunk chunk) {
        List<UUID> entries = read(chunk);
        if (entries == null || entries.isEmpty()) {
            return;
        }

        new BukkitRunnable(){

            @Override
            public void run() {
                for (UUID uuid : entries) {
                    if (!chunk.isLoaded()) {
                        break;
                    }

                    try {
                        barterManager.loadAndCacheContainer(new BarterStoreKeyImpl(uuid), (store) -> chunk.isLoaded()); // make sure chunk is loaded
                    } catch (Exception e) {
                        BarterContainer.INSTANCE.getLogger().warning(e.getMessage());
                        BarterContainer.INSTANCE.getLogger().warning("Failed to load BarterContainer: " + uuid);
                    }
                }
            }
        }.runTaskAsynchronously(BarterContainer.INSTANCE);
    }

    public void handleUnload(Chunk unloaded) {
        List<UUID> entries = read(unloaded);
        if (entries == null || entries.isEmpty()) {
            return;
        }

        barterManager.saveContainersAndUnload(entries);
    }

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

    private void write(List<UUID> values, Chunk chunk) {
        List<String> uuids = new ArrayList<>();
        for (UUID key : values) {
            uuids.add(key.toString());
        }
        chunk.getPersistentDataContainer().set(BARTER_KEYS, PersistentDataType.STRING, Strings.join(uuids, ','));
    }



}
