package com.stifflered.bartercontainer.util;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.store.BarterStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * BarterContainerLogger

 * Responsible for:
 *  • Emitting human-readable transaction logs to console.
 *  • Appending per-day log files to <pluginDir>/logs/yyyy-MM-dd.txt.
 *  • Appending per-owner log files to <pluginDir>/owners-logs/<ownerUUID>.txt.
 *  • Recording owner-facing purchase records via BarterShopOwnerLogManager.

 * Implementation notes:
 *  • All file I/O is offloaded to a single-threaded ExecutorService to avoid
 *    blocking the main server thread and to serialize writes safely.
 *  • The daily log file path is refreshed before each write in case the date
 *    has rolled over since the last write.
 *  • Log lines are prefixed with Instant.now() for precise timestamps.
 */
public class BarterContainerLogger {

    /** Single writer thread to serialize disk writes and keep main thread responsive. */
    private static final ExecutorService SERVICE = Executors.newSingleThreadExecutor();

    /** Current (daily) log file path; updated by {@link #updateLogFile()}. */
    private Path path;

    /** Console logger (Log4j). */
    private static final Logger LOGGER = LogManager.getLogger();

    /** Format for rotating the main log file by date (e.g., 2025-09-04.txt). */
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    public BarterContainerLogger() {
        this.updateLogFile();
    }

    /**
     * High-level transaction log entry.
     *  1) Log to console.
     *  2) Append to the rotating daily log file.
     *  3) Record a structured transaction for the store owner (async).
     *  4) Append a human-readable line to the owner-specific log file (async).
     *
     * @param player Buyer
     * @param bought Item bought (stack)
     * @param store  Store where the purchase happened
     */
    public void logTransaction(Player player, ItemStack bought, BarterStore store) {
        Location location = store.getLocations().isEmpty() ? null : store.getLocations().get(0);
        // Human-readable log line; includes buyer, store key, location, owner UUID, and price
        String log = "%s bought one %s from %s at %s owned by %s for %s".formatted(
                player.getName(), bought, store.getKey(), location, store.getPlayerProfile().getId(), store.getCurrentItemPrice()
        );
        LOGGER.info(log);        // 1) Console
        this.logToFile(log);     // 2) Daily file

        // 3) Owner-facing structured record (used by in-game book/log viewer)
        SERVICE.execute(() -> {
            try {
                // Include price ItemStack (v2 logs). Clone for safety.
                ItemStack price = store.getCurrentItemPrice();
                if (price != null) price = price.clone();

                BarterShopOwnerLogManager.addLog(
                        store.getKey(),
                        new BarterShopOwnerLogManager.TransactionRecord(
                                System.currentTimeMillis(),
                                player.getUniqueId(),
                                player.getName(),
                                bought.getType(),
                                bought.getAmount(),
                                price
                        )
                );
            } catch (IOException e) {
                // Surface unexpected file issues with context
                LOGGER.error("Failed to append owner-facing transaction record for store {}.",
                        store.getKey(), e);
                throw new RuntimeException(e);
            }
        });

        // 4) Append to per-owner text file (owners-logs/<ownerUUID>.txt)
        SERVICE.execute(() -> {
            try {
                UUID ownerUuid = (store.getPlayerProfile() != null) ? store.getPlayerProfile().getId() : null;
                if (ownerUuid == null) {
                    LOGGER.warn("Skipping owners-logs append: null owner UUID for store {}.", store.getKey());
                    return;
                }

                Path file = new File(BarterContainer.INSTANCE.getDataFolder(), "owners-logs").toPath()
                        .resolve(ownerUuid + ".txt");

                // Ensure directory and file exist
                Files.createDirectories(file.getParent());
                if (Files.notExists(file)) {
                    Files.createFile(file);
                }

                // Append with a precise timestamp
                try (FileWriter writer = new FileWriter(file.toFile(), true)) {
                    writer.write(Instant.now() + ": " + log + "\n");
                }
            } catch (IOException e) {
                LOGGER.error("Failed to append to owners-logs for store {}.", store.getKey(), e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Append a line to the current daily log file asynchronously.
     * Calls {@link #updateLogFile()} before writing to adapt to date rollovers.
     */
    private void logToFile(String log) {
        SERVICE.execute(() -> {
            this.updateLogFile(); // Ensure "path" points at today's file
            try (FileWriter writer = new FileWriter(this.path.toFile(), true)) {
                writer.write(Instant.now() + ": " + log + "\n");
            } catch (IOException e) {
                LOGGER.error("Failed to append to daily log file {}.", this.path, e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Compute and prepare the daily log file path:
     *   <pluginDir>/logs/yyyy-MM-dd.txt
     * Ensures the directory exists and the file is created if missing.
     */
    private void updateLogFile() {
        this.path = new File(BarterContainer.INSTANCE.getDataFolder(), "logs").toPath()
                .resolve(dateFormat.format(new Date()) + ".txt");

        try {
            Files.createDirectories(this.path.getParent());
            if (Files.notExists(this.path)) {
                Files.createFile(this.path);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to prepare daily log file at path {}.", this.path, e);
        }
    }

    /**
     * Stop the background writer thread and wait briefly for queued tasks to flush.
     * Call this from onDisable().
     */
    public static void shutdown() {
        SERVICE.shutdown();
        try {
            if (!SERVICE.awaitTermination(3, TimeUnit.SECONDS)) {
                LOGGER.warn("Logger executor did not terminate in time; forcing shutdownNow().");
                SERVICE.shutdownNow();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            SERVICE.shutdownNow();
        }
    }
}

