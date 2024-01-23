package com.stifflered.bartercontainer.store.owners;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.store.BarterStore;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class BankOwner implements InventoryHolder {

    private final BarterStore container;

    public BankOwner(BarterStore container) {
        this.container = container;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return this.container.getCurrencyStorage();
    }
}
