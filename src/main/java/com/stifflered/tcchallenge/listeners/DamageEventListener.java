package com.stifflered.tcchallenge.listeners;

import com.stifflered.tcchallenge.tree.OnlineTree;
import com.stifflered.tcchallenge.tree.TreeManager;
import com.stifflered.tcchallenge.tree.components.StatsStorage;
import com.stifflered.tcchallenge.tree.components.TreeComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class DamageEventListener implements Listener {

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        // Cancel fall damage for players if tree was made 10 seconds ago
        if (event.getEntity() instanceof Player player && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            OnlineTree tree = TreeManager.INSTANCE.getTreeIfLoaded(player);
            StatsStorage statsStorage = tree.getComponentStorage().get(TreeComponent.STATS);

            if (statsStorage.getCreated().isAfter(Instant.now().minus(10, ChronoUnit.SECONDS))) {
                event.setCancelled(true);
            }
        }
    }
}
