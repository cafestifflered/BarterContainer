package com.stifflered.bartercontainer.util;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.BarterContainerConfiguration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TrackingManager {

    private static TrackingManager instance = new TrackingManager();
    // Store tasks by player UUID
    private final Map<UUID, BukkitTask> trackingTasks = new ConcurrentHashMap<>();

    private final BarterContainerConfiguration.TrackingSystemConfiguration configuration = BarterContainer.INSTANCE.getConfiguration().getTrackingSystemConfiguration();

    public static TrackingManager instance() {
        return instance;
    }

    public void track(Player player, Location target) {
        untrack(player);
        player.sendMessage(configuration.startTracking());

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(BarterContainer.INSTANCE, new Runnable() {

            int i = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    untrack(player);
                    return;
                }

                Location eyeLocation = player.getLocation();
                if (!eyeLocation.getWorld().equals(target.getWorld())) {
                    return;
                }

                if (eyeLocation.distance(target) <= configuration.trackRange()) {
                    player.playSound(eyeLocation, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    target.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, target, 20, 0.5, 0.5, 0.5, 0);
                    untrack(player);
                    return;
                }

                if (i % 5 == 0) {
                    Vector direction = target.toVector().subtract(eyeLocation.toVector()).normalize();

                    double lineLength = configuration.arrowRange();
                    double step = 0.25;

                    for (double distance = 0; distance <= lineLength; distance += step) {
                        Location particleLoc = eyeLocation.clone().add(direction.clone().multiply(distance));
                        eyeLocation.getWorld().spawnParticle(Particle.FLAME, particleLoc, 1, 0, 0, 0, 0);
                    }
                }
                i++;
            }
        }, 0L, 1L);

        trackingTasks.put(player.getUniqueId(), task);
    }

    public void untrack(Player player) {
        BukkitTask task = trackingTasks.remove(player.getUniqueId());
        if (task != null) {
            player.sendMessage(configuration.endTracking());
            task.cancel();
        }
    }
}