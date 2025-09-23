package com.stifflered.bartercontainer.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/**
 * Utility class for spawning particle effects in the world.
 * <p>
 * Provides convenience methods for spawning both colored dust particles
 * (using RGB values) and arbitrary {@link Particle} types at a given {@link Location}.
 */
public class Particles {

    /**
     * Spawns a single {@link Particle#DUST} particle with the given RGB color
     * at the specified {@link Location}.
     * <p>
     * The particle is rendered with a fixed size of {@code 1.0}.
     *
     * @param location the {@link Location} where the particle will appear
     * @param r        the red component of the color (0–255)
     * @param g        the green component of the color (0–255)
     * @param b        the blue component of the color (0–255)
     */
    public static void spawnParticle(Location location, int r, int g, int b) {
        World world = location.getWorld();
        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(r, g, b), 1);

        world.spawnParticle(Particle.DUST, location, 1, dustOptions);
    }

    /**
     * Spawns a single instance of the given {@link Particle} at the specified {@link Location}.
     * <p>
     * This method does not apply additional data such as color or block/item type —
     * for particles requiring extra parameters (e.g., {@link Particle#REDSTONE} with custom colors),
     * use the Bukkit API directly.
     *
     * @param center   the {@link Location} where the particle will appear
     * @param particle the {@link Particle} type to spawn
     */
    public static void spawnParticle(Location center, Particle particle) {
        World world = center.getWorld();
        world.spawnParticle(particle, center, 1);
    }
}
