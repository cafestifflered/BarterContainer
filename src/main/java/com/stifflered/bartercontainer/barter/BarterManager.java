package com.stifflered.bartercontainer.barter;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.barter.serializers.InGameBarterSerializer;
import com.stifflered.bartercontainer.event.CreateBarterContainer;
import com.stifflered.bartercontainer.event.RemoveBarterContainer;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.store.BarterStoreImpl;
import com.stifflered.bartercontainer.store.BarterStoreKey;
import com.stifflered.bartercontainer.util.source.Sources;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BarterManager {

    public static final BarterManager INSTANCE = new BarterManager();

    private final InGameBarterSerializer serializer = new InGameBarterSerializer();

    private final Map<BarterStoreKey, BarterStore> storage = new ConcurrentHashMap<>();

    public void createNewStore(BarterStoreImpl barterStore, PersistentDataContainer persistentDataContainer, Chunk chunk) {
        this.serializer.writeBarterStoreKey(persistentDataContainer, barterStore);
        this.storage.put(barterStore.getKey(), barterStore);
        new CreateBarterContainer(barterStore, chunk).callEvent();
    }

    public boolean removeBarterContainer(Location location) {
        Optional<BarterStore> value = this.keyAtLocation(location)
                .map(storage::remove);

        if (value.isPresent()) {
            new RemoveBarterContainer(value.get(), location.getChunk()).callEvent();
            return true;
        }

        return false;
    }

    public void loadAndCacheContainer(BarterStoreKey barterStoreKey) throws Exception {
        BarterStore store = Sources.BARTER_STORAGE.load(barterStoreKey);
        this.storage.put(barterStoreKey, store);
    }

    public void saveContainersAndUnload(List<UUID> uuids) {
        new BukkitRunnable(){

            @Override
            public void run() {
                for (UUID uuid : uuids) {
                    BarterStore store = storage.remove(new BarterStoreKeyImpl(uuid));
                    try {
                        Sources.BARTER_STORAGE.save(store);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }.runTaskAsynchronously(BarterContainer.INSTANCE);
    }

    public void expungeContainer(UUID uuid) {
        this.storage.remove(new BarterStoreKeyImpl(uuid));
    }

    public Optional<BarterStore> getBarter(Location location) {
        return this.keyAtLocation(location)
                .map(this.storage::get);
    }

    private Optional<BarterStoreKey> keyAtLocation(Location location) {
        if (location.getBlock().getState(false) instanceof TileState tileState) {
            PersistentDataContainer container = tileState.getPersistentDataContainer();

            return this.serializer.getBarterStoreKey(container);
        }

        return Optional.empty();
    }

    public void saveAll() {
        storage.values().forEach(barterStore -> {
            try {
                Sources.BARTER_STORAGE.save(barterStore);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void save(BarterStore store) {
        try {
            Sources.BARTER_STORAGE.save(store);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
