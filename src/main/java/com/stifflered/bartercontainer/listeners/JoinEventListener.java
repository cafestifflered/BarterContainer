package com.stifflered.bartercontainer.listeners;

import com.stifflered.bartercontainer.item.ItemInstances;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinEventListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void playerJoin(PlayerJoinEvent event) {
        event.getPlayer().getInventory().addItem(ItemInstances.SHOP_LISTER_ITEM.getItem());
    }
}
