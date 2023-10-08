package com.stifflered.bartercontainer.barter;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.store.BarterStoreKey;
import org.bukkit.Location;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.HashMap;
import java.util.Map;

public class BarterManager {

    public static final BarterManager INSTANCE = new BarterManager();

    private final BarterSerializer serializer = new BarterSerializer();

    private final BiMap<BarterStoreKey, Player> locks = HashBiMap.create();
    private final Map<Location, BarterStore> barterStoreMap = new HashMap<>();


    @Nullable
    public BarterStore getBarter(Location location) {
        BarterStore barterStore = this.barterStoreMap.get(location);
        if (barterStore == null) {
            barterStore = this.getBarterAtLocation(location);
            this.barterStoreMap.put(location, barterStore);
        }

        return barterStore;
    }

    @Nullable
    private BarterStore getBarterAtLocation(Location location) {
        if (location.getBlock().getState(false) instanceof TileState tileState) {
            PersistentDataContainer container = tileState.getPersistentDataContainer();
            if (this.serializer.hasBarterStore(container)) {
                return this.serializer.getBarterStore(container);
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

    public BarterSerializer getSerializer() {
        return serializer;
    }

    public void removeBarter(Location location) {
        this.barterStoreMap.remove(location);
    }
}
