package com.stifflered.bartercontainer.util;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.function.Consumer;

/**
 * Utility for spawning and marking "safe" fireworks.
 * <p>
 * A safe rocket is a firework that detonates instantly and is marked with a
 * persistent tag so that plugins/listeners can identify and ignore its explosion
 * damage (see {@link com.stifflered.bartercontainer.listeners.SafeFireworkDamageListener}).
 */
public class SafeRocket {

    /** Persistent data key to mark fireworks as "safe". */
    private static final NamespacedKey SAFE_ROCKET_KEY = TagUtil.of("safe_rocket");

    /**
     * Spawns a firework at the given location, applies custom metadata,
     * and immediately detonates it.
     * <p>
     * The firework is marked with a persistent tag so other parts of the plugin
     * can cancel its damage effects.
     *
     * @param location the location to spawn the firework
     * @param meta     a consumer to modify the {@link FireworkMeta} before detonation
     */
    public static void instantRocket(Location location, Consumer<FireworkMeta> meta) {
        Firework firework = location.getWorld().spawn(location, Firework.class);
        FireworkMeta fireworkMeta = firework.getFireworkMeta();
        meta.accept(fireworkMeta);

        firework.setFireworkMeta(fireworkMeta);
        firework.detonate();

        // Mark this firework as safe so its damage can be ignored
        TagUtil.setIntegerTag(firework, SAFE_ROCKET_KEY, 0);
    }

    /**
     * Checks if a firework entity is tagged as "safe".
     *
     * @param firework the firework entity
     * @return true if the firework is a safe rocket, false otherwise
     */
    public static boolean isSafeRocket(Firework firework) {
        return TagUtil.hasTag(firework, SAFE_ROCKET_KEY);
    }
}
