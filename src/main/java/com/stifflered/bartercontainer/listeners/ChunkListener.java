package com.stifflered.bartercontainer.listeners;

import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.barter.ChunkBarterStorage;
import com.stifflered.bartercontainer.event.CreateBarterContainer;
import com.stifflered.bartercontainer.event.RemoveBarterContainer;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class ChunkListener implements Listener {

    private final ChunkBarterStorage chunkBarterStorage;

    public ChunkListener(ChunkBarterStorage chunkBarterStorage) {
        this.chunkBarterStorage = chunkBarterStorage;
    }

    @EventHandler
    public void onAdd(CreateBarterContainer event) {
        this.chunkBarterStorage.handleAdd(event.getChunk(), event.getContainer());
    }

    @EventHandler
    public void onRemove(RemoveBarterContainer event) {
        this.chunkBarterStorage.handleRemove(event.getChunk(), event.getContainer());
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        this.chunkBarterStorage.handleUnload(chunk);
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        this.chunkBarterStorage.handleLoad(chunk);
    }
}
