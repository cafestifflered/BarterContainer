package com.stifflered.tcchallenge.listeners;

import com.stifflered.tcchallenge.tree.TreeKey;
import com.stifflered.tcchallenge.tree.TreeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public class PlayerPreJoinEventListener implements Listener {

    @EventHandler
    public void playerJoin(AsyncPlayerPreLoginEvent event) {
        try {
            TreeManager.INSTANCE.getStorageManager().loadTreeBlocking(new TreeKey(event.getUniqueId()));
        } catch (Exception e) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text("Failed to load your tree! Contact an admin.", NamedTextColor.RED));
            e.printStackTrace();
        }
    }
}
