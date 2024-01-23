package com.stifflered.bartercontainer.listeners;

import com.stifflered.bartercontainer.store.owners.BankOwner;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;

import static org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY;

public class BarterInventoryListener implements Listener {

    @EventHandler
    public void protect(InventoryClickEvent event) {
        Inventory clickedInventory = event.getClickedInventory();
        if (event.getAction() == MOVE_TO_OTHER_INVENTORY && event.getView().getTopInventory().getHolder() instanceof BankOwner owner) {
            event.setCancelled(true);
            return;
        }

        if (clickedInventory != null && clickedInventory.getHolder() instanceof BankOwner owner) {
            boolean cancel = switch (event.getAction()) {
                case PICKUP_ALL -> false;
                default -> true;
            };

            if (event.getCurrentItem() == null) {
                cancel = true;
            }

            event.setCancelled(cancel);
        }
    }

    @EventHandler
    public void protect(InventoryMoveItemEvent event) {
        if (event.getDestination().getHolder() instanceof BankOwner owner) {
           event.setCancelled(true);
        }
    }

    @EventHandler
    public void protect(InventoryDragEvent event) {
        for (int slot : event.getRawSlots()) {
            if (event.getView().getInventory(slot).getHolder() instanceof BankOwner owner) {
                event.setCancelled(true);
            }
        }
    }


}
