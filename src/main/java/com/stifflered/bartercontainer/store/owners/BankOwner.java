package com.stifflered.bartercontainer.store.owners;

import com.stifflered.bartercontainer.store.BarterStore;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Custom {@link InventoryHolder} for a barter store’s "currency/bank" inventory.

 * Purpose:
 *  - Acts as the InventoryHolder when creating the currency inventory in {@link com.stifflered.bartercontainer.store.BarterStoreImpl}.
 *  - Provides Bukkit with a handle that can be traced back to the owning {@link BarterStore}.
 *  - Lets the plugin detect when an inventory belongs to a barter store’s bank (by checking instanceof BankOwner).

 * Behavior:
 *  - Always returns the barter store’s currency storage inventory from getInventory().

 * Notes:
 *  - Simpler than {@link SaveOnClose} because the currency inventory reference does not change,
 *    so no Supplier indirection is needed.
 *  - Primarily used as a tag/marker to distinguish the bank inventory from other inventories in events.
 */
public class BankOwner implements InventoryHolder {

    /** The barter store this bank inventory belongs to. */
    private final BarterStore container;

    public BankOwner(BarterStore container) {
        this.container = container;
    }

    /** Required Bukkit API method; delegates to the store’s currency storage. */
    @Override
    public @NotNull Inventory getInventory() {
        return this.container.getCurrencyStorage();
    }
}
