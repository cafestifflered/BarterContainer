package com.stifflered.bartercontainer.listeners;

import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.barter.ChunkBarterStorage;
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
