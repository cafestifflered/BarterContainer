package com.stifflered.bartercontainer.listeners;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.BarterShopOwnerLogManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.IOException;

public class JoinEventListener implements Listener {

    @EventHandler
    public void playerJoin(PlayerJoinEvent event) {
        BarterShopOwnerLogManager.notifyNewPurchases(event.getPlayer());
    }

    @EventHandler
    public void playerQuit(PlayerQuitEvent event) {
        BarterManager.INSTANCE.getOwnedShops(event.getPlayer()).thenAccept((shops) -> {
            for (BarterStore shop : shops) {
                try {
                    BarterShopOwnerLogManager.ackRecords(shop.getKey());
                } catch (IOException e) {
                    BarterContainer.INSTANCE.getLogger().warning("Failed to ack logs! " + e.getMessage() + " " + shop.getKey());
                }
            }

        });

    }
}
