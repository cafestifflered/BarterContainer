package com.stifflered.bartercontainer.store.owners;

import com.stifflered.bartercontainer.store.BarterStore;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class SaveOnClose implements InventoryHolder {

    private final BarterStore container;
    private final Supplier<Inventory> inventory;

    public SaveOnClose(BarterStore container, Supplier<Inventory> inventory) {
        this.container = container;
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return this.inventory.get();
    }

    public BarterStore getContainer() {
        return container;
    }
}
