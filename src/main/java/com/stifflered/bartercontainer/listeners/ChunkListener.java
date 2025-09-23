package com.stifflered.bartercontainer.listeners;

import com.stifflered.bartercontainer.barter.ChunkBarterStorage;
import com.stifflered.bartercontainer.event.CreateBarterContainer;
import com.stifflered.bartercontainer.event.RemoveBarterContainer;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * Listener that connects Bukkit world/chunk events and custom container events
 * to the {@link ChunkBarterStorage} system.

 * Purpose:
 *  - Ensures the per-chunk barter container index in PDC stays updated.
 *  - Handles lifecycle hooks when chunks load/unload.

 * Event flow:
 *  - CreateBarterContainer → handleAdd: append new store UUID to chunk index.
 *  - RemoveBarterContainer → handleRemove: remove store UUID, expunge from cache.
 *  - ChunkUnloadEvent → handleUnload: persist and evict stores from that chunk.
 *  - ChunkLoadEvent → handleLoad: schedule async load of stores recorded in that chunk’s PDC.

 * Notes:
 *  - Acts as the bridge between Bukkit’s world lifecycle and the plugin’s logical storage.
 *  - Keeps onEnable() clean since all registrations are consolidated here.
 */
public class ChunkListener implements Listener {

    /** Storage handler that reads/writes barter container IDs into chunk PDC. */
    private final ChunkBarterStorage chunkBarterStorage;

    public ChunkListener(ChunkBarterStorage chunkBarterStorage) {
        this.chunkBarterStorage = chunkBarterStorage;
    }

    /** On new store creation, add its UUID to the chunk’s index. */
    @EventHandler
    public void onAdd(CreateBarterContainer event) {
        this.chunkBarterStorage.handleAdd(event.getChunk(), event.getContainer());
    }

    /** On store removal, remove its UUID and evict from BarterManager cache. */
    @EventHandler
    public void onRemove(RemoveBarterContainer event) {
        this.chunkBarterStorage.handleRemove(event.getChunk(), event.getContainer());
    }

    /** On chunk unload, persist and unload all containers linked to that chunk. */
    @EventHandler
    public void onUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        this.chunkBarterStorage.handleUnload(chunk);
    }

    /** On chunk load, trigger async loading of containers recorded for that chunk. */
    @EventHandler
    public void onLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        this.chunkBarterStorage.handleLoad(chunk);
    }
}
