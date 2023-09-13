package com.stifflered.tcchallenge.listeners;

import com.stifflered.tcchallenge.tree.TreeKey;
import com.stifflered.tcchallenge.tree.TreeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinEventListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void playerJoin(PlayerJoinEvent event) {
        try {
            TreeManager.INSTANCE.getStorageManager().upgradeTree(new TreeKey(event.getPlayer().getUniqueId()));
        } catch (Exception e) {
            event.getPlayer().kick(Component.text("Failed to load your tree! Contact an admin.", NamedTextColor.RED));
            e.printStackTrace();
        }
    }
}
