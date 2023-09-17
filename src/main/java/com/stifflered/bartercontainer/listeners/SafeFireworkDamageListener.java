package com.stifflered.bartercontainer.listeners;

import com.stifflered.bartercontainer.util.SafeRocket;
import org.bukkit.entity.Firework;
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
