package com.stifflered.bartercontainer.util;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class ViewOnlyInventoryHolder implements InventoryHolder {

    private final Inventory inventory;

    public ViewOnlyInventoryHolder(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return this.inventory;
    }
}
