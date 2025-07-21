package com.stifflered.bartercontainer.barter;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.barter.serializers.InGameBarterSerializer;
import com.stifflered.bartercontainer.event.CreateBarterContainer;
import com.stifflered.bartercontainer.event.RemoveBarterContainer;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.store.BarterStoreImpl;
import com.stifflered.bartercontainer.store.BarterStoreKey;
import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.source.Sources;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

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
                new BukkitRunnable(){

                    @Override
                    public void run() {
                        try {
                            Sources.BARTER_STORAGE.delete(value.get());
                        } catch (Exception ignored) {
                        }
                    }
                }.runTaskAsynchronously(BarterContainer.INSTANCE);

            return true;
        }

        return false;
    }

    public void loadAndCacheContainer(BarterStoreKey barterStoreKey, Predicate<BarterStore> conditionCheck) throws Exception {
        BarterStore store = Sources.BARTER_STORAGE.load(barterStoreKey);
        if (store == null) {
            throw new IllegalStateException("Tried to load storage that doesnt exist!");
        }
        if (conditionCheck.test(store)) {
            this.storage.put(barterStoreKey, store);
        }
    }

    public void saveContainersAndUnload(List<UUID> uuids) {
        for (UUID uuid : uuids) {
            BarterStore store = storage.remove(new BarterStoreKeyImpl(uuid));
            if (store != null) {
                new BukkitRunnable(){

                    @Override
                    public void run() {
                        try {
                            Sources.BARTER_STORAGE.save(store);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }.runTaskAsynchronously(BarterContainer.INSTANCE);
            }
        }
    }

    public void expungeContainer(UUID uuid) {
        this.storage.remove(new BarterStoreKeyImpl(uuid));
    }


    public record ForceLoadResult(Optional<BarterStore> store, boolean forceLoading) {

    }

    public Optional<ForceLoadResult> getBarterAndForceTryToLoad(Location location) {
        Optional<BarterStoreKey> key = this.keyAtLocation(location);
        if (key.isEmpty()) {
            return Optional.of(new ForceLoadResult(Optional.empty(), false));
        } else {
            Optional<BarterStore> loaded = key.map(this.storage::get);
            if (loaded.isEmpty()) {
                BarterContainer.INSTANCE.getLogger().warning("NOTE: BARREL IS BEING FORCE LOADED " + key.get());
                new BukkitRunnable(){

                    @Override
                    public void run() {
                        try {
                            BarterManager.INSTANCE.loadAndCacheContainer(key.get(), (store) -> true);
                        } catch (Exception e) {
                            BarterContainer.INSTANCE.getLogger().warning(e.getMessage());
                            BarterContainer.INSTANCE.getLogger().warning("Failed to load BarterContainer: " + key);
                            e.printStackTrace();
                        }
                    }
                }.runTaskAsynchronously(BarterContainer.INSTANCE);

                return Optional.of(new ForceLoadResult(loaded, true));
            } else {
                return Optional.of(new ForceLoadResult(loaded, false));
            }
        }
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

    public List<BarterStore> getAll() throws RuntimeException {
        try {
            return Sources.BARTER_STORAGE.getAll();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
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
