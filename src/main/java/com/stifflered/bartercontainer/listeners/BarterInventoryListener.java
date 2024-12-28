package com.stifflered.bartercontainer.listeners;

import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.store.owners.BankOwner;
import com.stifflered.bartercontainer.store.owners.SaveOnClose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;

import static org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY;

public class BarterInventoryListener implements Listener {

    @EventHandler
    public void protect(InventoryClickEvent event) {
        Inventory clickedInventory = event.getClickedInventory();
        if (event.getAction() == MOVE_TO_OTHER_INVENTORY && event.getView().getTopInventory().getHolder() instanceof BankOwner) {
            event.setCancelled(true);
            return;
        }

        if (clickedInventory != null && clickedInventory.getHolder() instanceof BankOwner) {
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
        if (event.getDestination().getHolder() instanceof BankOwner) {
           event.setCancelled(true);
        }
    }

    @EventHandler
    public void protect(InventoryDragEvent event) {
        for (int slot : event.getRawSlots()) {
            if (event.getView().getInventory(slot).getHolder() instanceof BankOwner) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SaveOnClose save) {
            BarterManager.INSTANCE.save(save.getContainer());
        }
    }


}
