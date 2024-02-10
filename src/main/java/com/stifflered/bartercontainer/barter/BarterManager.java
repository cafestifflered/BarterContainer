package com.stifflered.bartercontainer.barter;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.store.BarterStoreImpl;
import com.stifflered.bartercontainer.store.BarterStoreKey;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.*;

public class BarterManager {

    public static final BarterManager INSTANCE = new BarterManager();

    private final BarterSerializer serializer = new BarterSerializer();

    private final BiMap<BarterStoreKey, Player> locks = HashBiMap.create();
    private final Map<Location, BarterStore> barterStoreMap = new HashMap<>();
    private final Map<Chunk, Set<BarterStore>> chunkMap = new HashMap<>();

    @Nullable
    public BarterStore getBarter(Location location) {
        BarterStore barterStore = this.barterStoreMap.get(location);
        if (barterStore == null) {
            barterStore = this.newBarterAtLocation(location);
        }

        if (barterStore != null) {
            this.barterStoreMap.put(location, barterStore);
            this.chunkMap.computeIfAbsent(location.getChunk(), (v) -> new HashSet<>()).add(barterStore);
        }

        return barterStore;
    }

    @Nullable
    private BarterStore newBarterAtLocation(Location location) {
        if (location.getBlock().getState(false) instanceof TileState tileState) {
            PersistentDataContainer container = tileState.getPersistentDataContainer();
            if (this.serializer.hasBarterStore(container)) {
                BarterStore store = this.serializer.getBarterStore(container);
                ((BarterStoreImpl) store).setLocation(location.clone());
                return store;
            }
        }

        return null;
    }

    public boolean lock(BarterStoreKey key, Player player) {
        if (this.locks.containsKey(key)) {
            return false;
        }

        this.locks.put(key, player);
        return true;
    }


    public boolean unlock(BarterStoreKey key) {
        if (this.locks.containsKey(key)) {
            this.locks.remove(key);
            return true;
        }

        return false;
    }

    public void unload(Chunk chunk) {
        Set<BarterStore> stores = this.chunkMap.remove(chunk);
        if (stores == null) {
            return;
        }

        for (BarterStore store : stores) {
            Location remove = store.getLocation();
            this.save(remove, this.barterStoreMap.remove(remove));
        }
    }

    public void save(Location location, BarterStore barterStore) {
        BlockState state = location.getBlock().getState(false);
        if (state instanceof TileState tileState) {
            BarterManager.INSTANCE.getSerializer().saveBarterStore(barterStore, tileState.getPersistentDataContainer());
        }
    }

    public BarterSerializer getSerializer() {
        return serializer;
    }

    public void removeBarter(Location location) {
        this.barterStoreMap.remove(location);
    }

    public void saveAll() {
        for (Map.Entry<Location, BarterStore> entry : this.barterStoreMap.entrySet()) {
            this.save(entry.getKey(), entry.getValue());
        }
    }
}
