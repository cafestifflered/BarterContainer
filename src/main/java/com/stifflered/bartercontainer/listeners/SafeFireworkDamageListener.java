package com.stifflered.bartercontainer.listeners;

import com.stifflered.bartercontainer.util.SafeRocket;
import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Listener that prevents certain fireworks from dealing damage.

 * Context:
 *  - Fireworks in Minecraft normally damage entities when they explode (especially when shot via crossbows).
 *  - This listener cancels damage if the firework has been marked as a "safe rocket."

 * Integration:
 *  - SafeRocket is a utility class that tags fireworks in some way (likely via PDC or metadata).
 *  - isSafeRocket(...) returns true if the given firework was created/launched in safe mode.

 * Effect:
 *  - Prevents fireworks spawned by the plugin (e.g., shop effects, GUI feedback) from harming players/mobs.
 */
public class SafeFireworkDamageListener implements Listener {

    /** Cancel damage events if the damager is a firework tagged as safe. */
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework) {
            if (SafeRocket.isSafeRocket(firework)) {
                event.setCancelled(true);
            }
        }
    }
}
