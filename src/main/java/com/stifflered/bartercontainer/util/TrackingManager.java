package com.stifflered.bartercontainer.util;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.BarterContainerConfiguration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the tracking system for guiding players towards a target {@link Location}.
 * <p>
 * Functionality:
 * <ul>
 *   <li>Starts particle + sound guidance towards a target.</li>
 *   <li>Runs scheduled tasks to continually update tracking visuals.</li>
 *   <li>Stops tracking automatically when player reaches the target range,
 *       logs out, or is manually untracked.</li>
 * </ul>
 * <p>
 * Configurable via {@link BarterContainerConfiguration.TrackingSystemConfiguration}.

 * NOTE (2025-09): Text for start/end messages has moved to messages.yml:
 *   tracking.start / tracking.end
 * Numeric ranges (track-range, arrow-range) remain in config.yml under tracking-system.
 */
public class TrackingManager {

    private static final TrackingManager instance = new TrackingManager();

    // Store tasks by player UUID
    private final Map<UUID, BukkitTask> trackingTasks = new ConcurrentHashMap<>();

    private final BarterContainerConfiguration.TrackingSystemConfiguration configuration =
            BarterContainer.INSTANCE.getConfiguration().getTrackingSystemConfiguration();

    /**
     * Get the singleton instance of the TrackingManager.
     */
    public static TrackingManager instance() {
        return instance;
    }

    /**
     * Begin tracking a target {@link Location} for the given player.
     * Displays flame particles in a line pointing to the target
     * and plays sounds/particles when the player reaches it.
     *
     * @param player the player to track for
     * @param target the target location
     */
    public void track(Player player, Location target) {
        // Cancel any existing task for this player
        untrack(player);

        // ⬇️ Messages now come from messages.yml (not config.yml)
        player.sendMessage(Messages.mm("tracking.start"));

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(BarterContainer.INSTANCE, new Runnable() {

            int i = 0; // tick counter to throttle particle updates

            @Override
            public void run() {
                // Stop tracking if the player is no longer online
                if (!player.isOnline()) {
                    untrack(player);
                    return;
                }

                Location eyeLocation = player.getLocation();

                // If the player and target are in different worlds, do nothing
                if (!eyeLocation.getWorld().equals(target.getWorld())) {
                    return;
                }

                // If the player is within the configured tracking range, mark success
                if (eyeLocation.distance(target) <= configuration.trackRange()) {
                    player.playSound(eyeLocation, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    target.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, target, 20, 0.5, 0.5, 0.5, 0);
                    untrack(player); // stop tracking now that they've arrived
                    return;
                }

                // Every 5 ticks, draw a line of flame particles pointing towards the target
                if (i % 5 == 0) {
                    Vector direction = target.toVector().subtract(eyeLocation.toVector()).normalize();

                    double lineLength = configuration.arrowRange(); // how far to draw the "arrow"
                    double step = 0.25; // spacing between particles

                    for (double distance = 0; distance <= lineLength; distance += step) {
                        Location particleLoc = eyeLocation.clone().add(direction.clone().multiply(distance));
                        eyeLocation.getWorld().spawnParticle(Particle.FLAME, particleLoc, 1, 0, 0, 0, 0);
                    }
                }
                i++;
            }
        }, 0L, 1L); // run every tick

        trackingTasks.put(player.getUniqueId(), task);
    }

    /**
     * Stop tracking for a player.
     * Cancels and removes any active task, and sends the end message.
     *
     * @param player the player to stop tracking
     */
    public void untrack(Player player) {
        BukkitTask task = trackingTasks.remove(player.getUniqueId());
        if (task != null) {
            // ⬇️ Messages now come from messages.yml (not config.yml)
            player.sendMessage(Messages.mm("tracking.end"));
            task.cancel();
        }
    }
}
