package com.stifflered.bartercontainer.store.owners;

import com.stifflered.bartercontainer.store.BarterStore;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Custom {@link InventoryHolder} used for barter store "sale" inventories.

 * Purpose:
 *  - Attached as the InventoryHolder when creating the Bukkit Inventory for a store’s sale slots.
 *  - Provides access back to the owning {@link BarterStore}.
 *  - Supplies the underlying Inventory via a {@link Supplier}, so the holder always reflects
 *    the current inventory reference (even if replaced/reset).

 * Usage:
 *  - Constructed in {@link com.stifflered.bartercontainer.store.BarterStoreImpl} for the 27-slot sale inventory:
 *      Bukkit.createInventory(new SaveOnClose(this, () -> this.itemStacks), 27);
 *  - Likely leveraged by inventory close listeners to detect when a store GUI is closed and trigger persistence.

 * Notes:
 *  - The Supplier indirection avoids stale references if the internal Inventory object changes.
 *  - Does not itself perform save logic—other parts of the plugin must detect close events
 *    and use getContainer() to decide what to persist.
 */
public class SaveOnClose implements InventoryHolder {

    /** The logical barter store this inventory belongs to. */
    private final BarterStore container;

    /** Supplier that returns the current backing Inventory object. */
    private final Supplier<Inventory> inventory;

    public SaveOnClose(BarterStore container, Supplier<Inventory> inventory) {
        this.container = container;
        this.inventory = inventory;
    }

    /** Required Bukkit API method; returns the Inventory this holder represents. */
    @Override
    public @NotNull Inventory getInventory() {
        return this.inventory.get();
    }

    /** Accessor for the owning barter store. */
    public BarterStore getContainer() {
        return container;
    }
}
