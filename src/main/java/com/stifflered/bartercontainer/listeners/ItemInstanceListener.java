package com.stifflered.bartercontainer.listeners;

import com.stifflered.bartercontainer.item.ItemInstance;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class ItemInstanceListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public final void onInteract(PlayerInteractEvent event) {
        ItemInstance instance = this.get(event.getItem());
        if (instance != null) {
            instance.interact(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public final void onPlace(BlockPlaceEvent event) {
        ItemInstance instance = this.get(event.getItemInHand());
        if (instance != null) {
            instance.place(event);
        }
    }

    private ItemInstance get(ItemStack stack) {
        return ItemInstance.fromStack(stack);
    }
}
