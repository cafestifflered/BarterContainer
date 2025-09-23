package com.stifflered.bartercontainer.listeners;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.BarterShopOwnerLogManager;
import com.stifflered.bartercontainer.util.TrackingManager;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.IOException;

/**
 * Listener for player connection events that ties into shop notifications, head preloading, and tracking.
 *
 * Responsibilities:
 *  - On join:
 *      * Notify shop owners of any purchases that occurred while they were offline.
 *      * Warm Bedrock skin cache so GUI heads render without Steve/Alex (config-gated).
 *  - On quit:
 *      * For each shop owned by the player, acknowledge any unacknowledged purchase logs.
 *      * Untrack the player from the tracking system (e.g., for navigation/arrows).
 *
 * Priority:
 *  - Default priority; runs after most core join/quit logic but before MONITOR listeners.
 */
public class JoinEventListener implements Listener {

    /** On join: notify owners; optionally preload Bedrock heads depending on config. */
    @EventHandler
    public void playerJoin(PlayerJoinEvent event) {
        // Notify shop owners of purchases while they were offline.
        BarterShopOwnerLogManager.notifyNewPurchases(event.getPlayer());

        // Preload Bedrock head textures on join (fire-and-forget), if enabled.
        // Config key: head-cache.preload-on-join (default true).
        var plugin = BarterContainer.INSTANCE;
        if (plugin.getConfig().getBoolean("head-cache.preload-on-join", true) && plugin.getHeadService() != null) {
            var uuid = event.getPlayer().getUniqueId();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getHeadService().ensureCachedBedrock(uuid));
        }
    }

    /**
     * On quit:
     *  - Async-fetch the shops owned by the quitting player.
     *  - Acknowledge (ack) their shop logs so notifications won’t repeat.
     *  - Stop tracking the player in the tracking system.
     */
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

        TrackingManager.instance().untrack(event.getPlayer());
    }
}
