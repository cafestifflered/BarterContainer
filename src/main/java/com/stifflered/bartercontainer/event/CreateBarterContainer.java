package com.stifflered.bartercontainer.event;

import com.stifflered.bartercontainer.store.BarterStore;

import org.bukkit.Chunk;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import org.jetbrains.annotations.NotNull;

/**
 * Custom Bukkit event fired whenever a new {@link BarterStore} is created and registered.

 * Purpose:
 *  - Allows other plugins or subsystems inside BarterContainer to listen and react
 *    when a player/shop establishes a new barter container.
 *  - Carries both the logical container object and the physical chunk it belongs to.

 * Standard Bukkit custom event pattern:
 *  - Extends {@link Event}.
 *  - Provides static HandlerList and getHandlerList() boilerplate.

 * Notes:
 *  - This event is called explicitly in {@link com.stifflered.bartercontainer.barter.BarterManager#createNewStore}.
 *  - Does not include the Player who created it (if needed, must be tracked at higher level).
 *  - Other imports like Block, Player, BlockBreakEvent are unused here—possibly left over.
 */
public class CreateBarterContainer extends Event {

    /** Required boilerplate handler list for all Bukkit events. */
    private static final HandlerList handlers = new HandlerList();

    /** The logical barter store object being created. */
    private final BarterStore container;

    /** The chunk that contains the container's physical block(s). */
    private final Chunk chunk;

    public CreateBarterContainer(BarterStore container, Chunk chunk) {
        this.container = container;
        this.chunk = chunk;
    }

    /** Accessor for the newly created container. */
    public BarterStore getContainer() {
        return container;
    }

    /** Accessor for the chunk containing this container. */
    public Chunk getChunk() {
        return chunk;
    }

    /** Required Bukkit API method for handler list resolution. */
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    /** Required Bukkit API static method for handler list resolution. */
    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}
