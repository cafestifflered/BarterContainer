package com.stifflered.tcchallenge.listeners;

import com.stifflered.tcchallenge.util.SafeRocket;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class SafeFireworkDamageListener implements Listener {

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework) {
            if (SafeRocket.isSafeRocket(firework)) {
                event.setCancelled(true);
            }
        }
    }
}
