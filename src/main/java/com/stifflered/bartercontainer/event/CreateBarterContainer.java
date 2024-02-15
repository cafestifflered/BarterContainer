package com.stifflered.bartercontainer.event;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.store.BarterStore;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockBreakEvent;
import org.jetbrains.annotations.NotNull;

public class CreateBarterContainer extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final BarterStore container;
    private final Chunk chunk;

    public CreateBarterContainer(BarterStore container, Chunk chunk) {
        this.container = container;
        this.chunk = chunk;
    }

    public BarterStore getContainer() {
        return container;
    }

    public Chunk getChunk() {
        return chunk;
    }

    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}
