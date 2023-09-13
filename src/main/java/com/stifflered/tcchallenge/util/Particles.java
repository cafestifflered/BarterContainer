package com.stifflered.tcchallenge.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

public class Particles {

    public static void spawnParticle(Location location, int r, int g, int b) {
        World world = location.getWorld();
        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(r, g, b), 1);

        world.spawnParticle(Particle.REDSTONE, location, 1, dustOptions);
    }

    public static void spawnParticle(Location center, Particle particle) {
        World world = center.getWorld();

        world.spawnParticle(particle, center, 1);
    }
}
