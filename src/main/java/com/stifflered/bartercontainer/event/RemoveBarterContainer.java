package com.stifflered.bartercontainer.event;

import com.stifflered.bartercontainer.store.BarterStore;

import org.bukkit.Chunk;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import org.jetbrains.annotations.NotNull;

/**
 * Custom Bukkit event fired whenever a {@link BarterStore} is removed/unregistered.

 * Purpose:
 *  - Provides a hook for other systems to react when a shop is dismantled, deleted, or removed.
 *  - Carries both the logical container object and the chunk it was located in.

 * Lifecycle:
 *  - Fired in {@link com.stifflered.bartercontainer.barter.BarterManager#removeBarterContainer}.
 *  - Typically dispatched after the store is removed from the runtime cache.

 * Notes:
 *  - Follows standard Bukkit custom event boilerplate with static HandlerList.
 *  - Similar to {@link CreateBarterContainer}, but indicates teardown instead of creation.
 */
public class RemoveBarterContainer extends Event {

    /** Required boilerplate handler list for Bukkit events. */
    private static final HandlerList handlers = new HandlerList();

    /** The barter store being removed. */
    private final BarterStore container;

    /** The chunk that contained the barter store. */
    private final Chunk chunk;

    public RemoveBarterContainer(BarterStore container, Chunk chunk) {
        this.container = container;
        this.chunk = chunk;
    }

    /** Accessor for the store that was removed. */
    public BarterStore getContainer() {
        return container;
    }

    /** Accessor for the chunk that contained the removed store. */
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
