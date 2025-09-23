package com.stifflered.bartercontainer;

import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.barter.ChunkBarterStorage;
import com.stifflered.bartercontainer.command.*;
import com.stifflered.bartercontainer.commands.DirectoryCommand; // <-- NEW: /directory command executor
import com.stifflered.bartercontainer.item.ItemInstances;
import com.stifflered.bartercontainer.listeners.*;
import com.stifflered.bartercontainer.player.ShoppingListManager;
import com.stifflered.bartercontainer.util.BarterContainerLogger;
import com.stifflered.bartercontainer.util.Messages;
import com.stifflered.bartercontainer.util.analytics.WeeklyConsistencySnapshot;
import com.stifflered.bartercontainer.util.skin.HeadService;
import com.stifflered.bartercontainer.util.TimeUtil;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import java.util.Objects;

/**
 * Primary plugin entry point for the BarterContainer plugin.

 * Responsibilities:
 *  - Hold a static plugin INSTANCE for global access across the codebase.
 *  - Initialize domain managers (ChunkBarterStorage, ShoppingListManager).
 *  - Register Bukkit/Paper event listeners.
 *  - Register Bukkit command executors (delegated to command classes).
 *  - Trigger item singletons to ensure custom items are loaded.
 *  - Persist barter data on shutdown.

 * Notes:
 *  - This class does NOT implement commands/listeners directly; it wires them together.
 *  - WorldGuard integration is stubbed (commented) but the presence check exists.

 * NEW (Analytics):
 *  - Wires in a weekly, offline-safe job that snapshots each owner's ALL-BARRELS consistency
 *    into a monthly CSV (columns: Player,Week1..Week4,Grand,Month), ordered by Player.
 *  - The job does not rely on players being online. It reads per-store logs and aggregates by owner UUID.
 */
public class BarterContainer extends JavaPlugin implements Listener {

    /** Display name for an admin/root entity elsewhere in the plugin domain. */
    public static final String adminTreeName = "The TreeCrafter";

    /**
     * Global plugin reference (classic Bukkit pattern).
     * Set during onEnable() and used by other components to access plugin context/scheduler/config.
     */
    public static BarterContainer INSTANCE;

    /** Wrapper for plugin configuration. Constructed after plugin enable; provides typed access to config values. */
    private BarterContainerConfiguration configuration;

    /** Handles player shopping lists (add/remove/query), likely persisted per-player. */
    private ShoppingListManager shoppingListManager;

    /** Single HeadService instance shared across the plugin. */
    private HeadService headService;

    // Analytics job kept as a field (referenced by scheduled tasks and command).
    private WeeklyConsistencySnapshot consistencyJob;

    @Override
    public void onEnable() {
        // Establish the static plugin reference for access across the project.
        INSTANCE = this;

        // Ensure messages.yml is prepared before anything tries to use it.
        Messages.init(this);

        // Ensure config is present and loaded before any component tries to read it.
        // This guarantees transactions.time_pattern is available for TimeUtil initialization.
        saveDefaultConfig();
        TimeUtil.reloadFromConfig(getConfig()); // <-- Initialize absolute timestamp formatter (UTC) from config

        // Load configuration wrapper early so any components that query it during init are safe.
        this.configuration = new BarterContainerConfiguration(this);

        // Initialize chunk-scoped barter storage (local final to avoid "field could be local" warning).
        final ChunkBarterStorage chunkBarterStorage = new ChunkBarterStorage(BarterManager.INSTANCE);

        // Initialize shopping list subsystem; provides per-player list management and utilities.
        this.shoppingListManager = new ShoppingListManager(this);

        // Create HeadService early so listeners can use it immediately.
        this.headService = new HeadService(this);

        // === Register commands via plugin.yml ===
        // Root admin: /barterbarrels <sub>
        var barterBarrelsCmd = Objects.requireNonNull(
                getCommand("barterbarrels"),
                "barterbarrels command missing in plugin.yml"
        );
        var root = new BarterBarrelsCommand();
        barterBarrelsCmd.setExecutor(root);
        barterBarrelsCmd.setTabCompleter(root);

        // /catalog (everyone by default; gated by node that defaults to true)
        Objects.requireNonNull(getCommand("catalog"), "catalog command missing in plugin.yml")
                .setExecutor(new CatalogueCommand());

        // /directory (everyone by default; gated by node that defaults to true)
        Objects.requireNonNull(getCommand("directory"), "directory command missing in plugin.yml")
                .setExecutor(new DirectoryCommand(this));

        // /shoppinglist (everyone by default; gated by node that defaults to true) — temporarily disabled
        // Objects.requireNonNull(getCommand("shoppinglist"), "shoppinglist command missing in plugin.yml")
        //        .setExecutor(new ShoppingListCommand());

        // /endtracking (everyone by default)
        Objects.requireNonNull(getCommand("endtracking"), "endtracking command missing in plugin.yml")
                .setExecutor(new EndTrackingCommand());

        // Register gameplay listeners.
        this.register(
                new ItemInstanceListener(),
                new JoinEventListener(),
                new SafeFireworkDamageListener(),
                new BarterBlockListener(),
                new BarterInventoryListener(),
                new ChunkListener(chunkBarterStorage) // reacts to chunk load/unload
        );

        // Optional WorldGuard hook (currently disabled). Add a fine log to avoid "empty if" warning.
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            getLogger().fine(Messages.fmt("integrations.worldguard_detected"));
            // worldGuardHook = new WorldGuardHook();
            // log("Hooked into World Guard");
        }

        // Force-load item singletons so any static init / NBT / meta setup happens eagerly on startup.
        // Using the result avoids "Result of method call ignored" warning.
        Objects.requireNonNull(ItemInstances.SHOP_LISTER_ITEM.getItem(), "SHOP_LISTER_ITEM must initialize");

        // === Analytics: Weekly consistency snapshot wiring ===
        final ZoneId analyticsZone = ZoneId.systemDefault();
        final Clock analyticsClock = Clock.systemUTC();

        this.consistencyJob = new WeeklyConsistencySnapshot(this, analyticsZone, analyticsClock);

        // Schedule the job asynchronously to fire Monday 00:05 server local time.
        Bukkit.getScheduler().runTaskTimerAsynchronously(
                this,
                () -> {
                    ZonedDateTime now = ZonedDateTime.now(analyticsZone);
                    if (now.getDayOfWeek() == java.time.DayOfWeek.MONDAY
                            && now.getHour() == 0
                            && now.getMinute() == 5) {
                        try {
                            getLogger().info(Messages.fmt("analytics.consistency.snapshot_started_log"));
                            consistencyJob.runNow();
                        } catch (Throwable t) {
                            getLogger().warning(Messages.fmt("analytics.consistency.snapshot_failed_log", "detail", t.getMessage()));
                        }
                    }
                },
                20L,          // initial delay: 1 second
                20L * 60L     // repeat: every 60 seconds
        );

        // Optional: keep the manual admin command for analytics on demand.
        Bukkit.getCommandMap().register("consistency-snapshot", new Command("consistency-snapshot") {
            @Override
            public boolean execute(@NotNull CommandSender sender,
                                   @NotNull String label,
                                   @NotNull String[] args) {
                Bukkit.getScheduler().runTaskAsynchronously(BarterContainer.this, () -> {
                    try {
                        consistencyJob.runNow();
                        sender.sendMessage(Messages.fmt("analytics.consistency.snapshot_ok_command"));
                    } catch (Throwable t) {
                        sender.sendMessage(Messages.fmt("analytics.consistency.snapshot_failed_command", "detail", t.getMessage()));
                    }
                });
                return true;
            }
        });
    }

    @Override
    public void onDisable() {
        // Ensure all barter state is flushed to persistent storage before shutdown.
        BarterManager.INSTANCE.saveAll();

        // Cleanly stop the single-writer executor so the JVM can exit without lingering threads.
        BarterContainerLogger.shutdown();
    }

    /** World scoping guard for "challenge" worlds. */
    public boolean isChallengeWorld(World world) {
        return true;
    }

    /** Utility to bulk-register event listeners. */
    private void register(Listener... listeners) {
        for (Listener listener : listeners) {
            Bukkit.getPluginManager().registerEvents(listener, this);
        }
    }

    /** Accessor for the typed configuration facade. */
    public BarterContainerConfiguration getConfiguration() {
        return configuration;
    }

    /** Accessor for shopping list manager. */
    public ShoppingListManager getShoppingListManager() {
        return shoppingListManager;
    }

    public HeadService getHeadService() {
        return headService;
    }
}
