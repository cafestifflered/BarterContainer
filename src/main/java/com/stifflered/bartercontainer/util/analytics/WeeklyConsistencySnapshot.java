package com.stifflered.bartercontainer.util.analytics;

import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.store.BarterStoreKey;
import com.stifflered.bartercontainer.util.BarterShopOwnerLogManager;
import com.stifflered.bartercontainer.util.BarterShopOwnerLogManager.TransactionRecord;
import com.stifflered.bartercontainer.util.Messages;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.time.*;
import java.time.temporal.WeekFields;
import java.util.*;

/**
 * Computes each owner's ALL-BARRELS consistency over the configured window (default 7 days)
 * and writes it to the monthly CSV:
 *   Player, Week1..Week4, Grand, Month

 * Implementation is self-contained and relies on the existing APIs you provided:
 *   - Owners & stores:    BarterManager.INSTANCE.getAll()  →  group by owner UUID
 *   - Per-store logs:     BarterShopOwnerLogManager.listAllEntries(BarterStoreKey)
 *   - Scoring math:       ConsistencyScoreCalculator

 * Threading:
 *  - Intended to run asynchronously (schedule from the plugin with runTaskTimerAsynchronously).
 *  - Avoids Bukkit calls; uses only your storage APIs and file I/O off-thread.
 */
public final class WeeklyConsistencySnapshot {

    private final Plugin plugin;
    private final ConsistencyArchive archive;
    private final ZoneId zone;
    private final Clock clock;

    /** Scoring params (controls windowDays, lambda, etc.). Default = 7-day window. */
    private final ConsistencyScoreCalculator.Params params;

    /** Uses default scoring parameters (7-day window with tail-weighted decay). */
    public WeeklyConsistencySnapshot(Plugin plugin, ZoneId zone, Clock clock) {
        this(plugin, zone, clock, ConsistencyScoreCalculator.Params.DEFAULT());
    }

    /** Allows custom scoring parameters (e.g., different windowDays). */
    public WeeklyConsistencySnapshot(Plugin plugin, ZoneId zone, Clock clock,
                                     ConsistencyScoreCalculator.Params params) {
        this.plugin = plugin;
        this.archive = new ConsistencyArchive(plugin);
        this.zone = zone;
        this.clock = clock;
        this.params = params;
    }

    /** Execute a snapshot "now": compute week index, aggregate by owner, score, and append to the month file. */
    public void runNow() {
        // Determine current YearMonth and the "week index" within the month.
        LocalDate today = LocalDate.now(zone);
        YearMonth ym = YearMonth.from(today);
        int weekIndex = Math.min(4, weekOfMonth(today)); // clamp any 5th week to Week4 to keep 4 columns

        // Build owner → stores index and owner → name map from authoritative storage
        Index idx = buildOwnerIndex();

        if (idx.ownerToKeys.isEmpty()) {
            plugin.getLogger().info(Messages.fmt(
                    "analytics.consistency.snapshot_empty",
                    "month", ym.toString()
            ));
            return;
        }

        final int windowDays = Math.max(1, params.windowDays());

        // For each owner → compute last-N-days all-barrels consistency and record
        for (UUID ownerId : idx.ownerToKeys.keySet()) {
            String ownerName = idx.ownerToName.getOrDefault(ownerId, ownerId.toString());

            List<BarterStoreKey> keys = idx.ownerToKeys.get(ownerId);
            List<Instant> events = loadOwnerSalesWithinDays(keys, windowDays);

            ConsistencyScoreCalculator.Result r =
                    ConsistencyScoreCalculator.calculateFromInstants(events, params, clock, zone);

            double weeklyScore = r.finalScore();

            try {
                archive.recordWeeklyScore(ownerName, ym, weekIndex, weeklyScore);
            } catch (IOException io) {
                plugin.getLogger().warning(Messages.fmt(
                        "analytics.consistency.snapshot_failed_record",
                        "owner", ownerName,
                        "detail", io.getMessage()
                ));
            }
        }

        plugin.getLogger().info(Messages.fmt(
                "analytics.consistency.snapshot_complete",
                "month", ym.toString(),
                "week", weekIndex
        ));
    }

    /** Week-of-month: 1..5 based on ISO week fields. */
    private int weekOfMonth(LocalDate date) {
        LocalDate first = date.withDayOfMonth(1);
        WeekFields wf = WeekFields.ISO;
        int firstWeek = first.get(wf.weekOfWeekBasedYear());
        int curWeek = date.get(wf.weekOfWeekBasedYear());
        int diff = curWeek - firstWeek;
        if (diff < 0) diff += 53; // handle year wrap
        return diff + 1;
    }

    /**
     * Aggregate all sale timestamps for the given stores within the last {@code windowDays}.
     * Pulls per-store transaction records and filters by cutoff ← now - windowDays.
     */
    private List<Instant> loadOwnerSalesWithinDays(List<BarterStoreKey> keys, int windowDays) {
        Instant now = Instant.now(clock);
        Instant cutoff = now.minus(Duration.ofDays(windowDays));

        List<Instant> out = new ArrayList<>();
        for (BarterStoreKey key : keys) {
            try {
                List<TransactionRecord> tx = BarterShopOwnerLogManager.listAllEntries(key); // oldest → newest
                for (TransactionRecord tr : tx) {
                    Instant t = Instant.ofEpochMilli(tr.timestamp());
                    if (!t.isBefore(cutoff) && !t.isAfter(now)) {
                        out.add(t);
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning(Messages.fmt(
                        "analytics.consistency.snapshot_read_error_store",
                        "key", String.valueOf(key),
                        "detail", e.getMessage()
                ));
            }
        }
        return out;
    }

    /** Build from storage: owner UUID → keys and owner UUID → display name (prefer PlayerProfile.name). */
    private Index buildOwnerIndex() {
        Map<UUID, List<BarterStoreKey>> ownerToKeys = new HashMap<>();
        Map<UUID, String> ownerToName = new HashMap<>();

        // Pull every store from your persistent storage (your getAll() already catches & returns empty on error).
        List<BarterStore> all = BarterManager.INSTANCE.getAll();

        for (BarterStore store : all) {
            UUID ownerId = store.getPlayerProfile().getId();
            String name = store.getPlayerProfile().getName(); // may be null for brand-new/offline profiles

            ownerToKeys.computeIfAbsent(ownerId, __ -> new ArrayList<>()).add(store.getKey());

            // Record a readable name the first time we see one; fallback to UUID later if still null.
            if (name != null && !name.isBlank()) {
                ownerToName.putIfAbsent(ownerId, name);
            }
        }

        // Ensure every owner has some display string
        for (UUID owner : ownerToKeys.keySet()) {
            ownerToName.putIfAbsent(owner, owner.toString());
        }

        // Sort each owner's keys deterministically (by UUID string) just for stable iteration
        for (List<BarterStoreKey> keys : ownerToKeys.values()) {
            keys.sort(Comparator.comparing(k -> k.key().toString()));
        }

        return new Index(ownerToKeys, ownerToName);
    }

    /** Tiny holder for the two parallel indexes. */
    private record Index(Map<UUID, List<BarterStoreKey>> ownerToKeys,
                         Map<UUID, String> ownerToName) {}
}
