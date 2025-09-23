package com.stifflered.bartercontainer.gui.tree;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.store.BarterStoreKey;
import com.stifflered.bartercontainer.util.BarterShopOwnerLogManager;
import com.stifflered.bartercontainer.util.analytics.ConsistencyScoreCalculator;
import com.stifflered.bartercontainer.util.Messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.time.*;
import java.util.*;

/**
 * "All Shops — Stats" GUI (27 slots).

 * Mirrors ShopStatsGui but aggregates across MANY stores (all barrels for this owner).

 * Key (visual layout):
 *   Row 1 (0..8):  [Filler(0)] [Chest(1)] [ ] [Clock(3)] [ ] [Ender Eye(5)] [ ] [Glow Item Frame(7)] [ ]
 *   Row 2 (9..17): [Nether Star(9)] [ ] [Green Dye(11)] [Cyan Dye(13)] [Blue Dye(15)] [ ] [Paper(17)]
 *   Row 3 (18..26): fillers ... [Barrier(22)] ...

 * Mappings:
 *   Filler (0)        → Plain filler only (no avatar in this menu)
 *   Chest (1)         → Total Sales (ALL-TIME units + transactions) across ALL stores
 *   Clock (3)         → 🕓 Last Sale (item, qty, buyer, absolute, relative) across ALL stores
 *   Ender Eye (5)     → Unique Purchasers Count (distinct UUIDs, ALL-TIME) across ALL stores
 *   Glow Item Frame(7)→ Top-Selling Item (by Material, ALL-TIME) across ALL stores
 *   Paper (17)        → Reserved “More (TBD)”
 *   Barrier (22)      → Back (handled by central listener)

 * - Clicks on the Nether Star / Green / Cyan / Blue tiles now print **aggregate** chat breakdowns
 *   (same math and phrasing as single-shop, but across all stores). See the "NEW: Click handling"
 *   section near the bottom for listener wiring and helper methods.
 */
public final class AllShopStatsGui {

    /** Prefix into messages.yml for this GUI block. */
    private static final String K = "gui.all_shop_stats.";

    /** Convenience wrappers that automatically prepend the section key. */
    private static Component MM(String key, Object... kv) { return Messages.mm(K + key, kv); }
    private static List<Component> MML(String key) { return Messages.mmList(K + key); }

    /** Absolute timestamp label (pattern/zone centralized). */
    private static java.time.format.DateTimeFormatter ABS_FMT() {
        return com.stifflered.bartercontainer.util.TimeUtil.absoluteFormatter();
    }

    // Public slot constants (reused by central listener)
    public static final int SLOT_OVERALL   = 9;   // Nether Star
    public static final int SLOT_STABILITY = 11;  // Green Dye
    public static final int SLOT_RECENCY   = 13;  // Cyan Dye
    public static final int SLOT_TREND     = 15;  // Blue Dye

    /** Title from messages.yml: gui.all_shop_stats.title */
    private static Component title() {
        return MM("title");
    }

    /**
     * Holder carries the data we need to navigate back to the All-barrels hub:
     *  - keys: the complete set of BarterStoreKey being aggregated
     *  - ownerLabel: optional name to show in titles
     *  - originKey: the single barrel we came from (enables "Back to This Barrel" in the hub)
     *  - stores: (NEW) the fully loaded BarterStore list used to render this view (lets clicks avoid reloading logs)

     * This lets the listener rebuild AllLogBookHubGui when the user clicks the Barrier (slot 22).
     */
    public static final class Holder implements InventoryHolder {
        private final Set<BarterStoreKey> keys;
        private final String ownerLabel;
        private final BarterStoreKey originKey;
        /** Loaded stores for this view (transient, not serialized). */
        private final List<BarterStore> stores;
        private Inventory inv;

        public Holder(Collection<BarterStoreKey> keys, String ownerLabel, BarterStoreKey originKey, List<BarterStore> stores) {
            this.keys = Set.copyOf(Objects.requireNonNull(keys, "keys"));
            this.ownerLabel = ownerLabel;
            this.originKey = originKey; // may be null
            this.stores = (stores == null) ? List.of() : List.copyOf(stores);
        }

        public Set<BarterStoreKey> getKeys() { return keys; }
        public String getOwnerLabel() { return ownerLabel; }
        public BarterStoreKey getOriginKey() { return originKey; }
        /** Accessor for preloaded stores (may be empty). */
        public List<BarterStore> getStores() { return stores; }

        // NEW: ensure the same Inventory instance that was opened is returned here
        void attach(Inventory inv) { this.inv = inv; }

        @Override
        public @org.jetbrains.annotations.NotNull Inventory getInventory() {
            if (inv == null) inv = Bukkit.createInventory(this, 27, title());
            return inv;
        }
    }

    /**
     * open(...) accepts the context needed for "Back":
     *  - stores: fully loaded BarterStore objects to compute stats
     *  - keys / ownerLabel / originKey: to rebuild the AllLogBookHubGui on Barrier click
     */
    public static void open(Player player,
                            List<BarterStore> stores,
                            Collection<BarterStoreKey> keys,
                            String ownerLabel,
                            BarterStoreKey originKey) {
        if (stores == null || stores.isEmpty()) {
            player.sendMessage(MM("msg_no_shops"));
            return;
        }

        // stash the loaded stores list in the Holder so clicks can reuse it
        Holder holder = new Holder(keys, ownerLabel, originKey, stores); // NEW (use holder instance)
        Inventory inv = Bukkit.createInventory(holder, 27, title());     // NEW (create inv separately)
        holder.attach(inv);                                              // NEW (tie inv to holder)

        // Fillers first (so the player sees an immediate UI shell)
        ItemStack filler = pane();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        player.openInventory(inv);

        // ----------------------------------------------------------------------------------------
        // Heavy aggregation (reading logs, computing stats) MUST NOT run on the server thread.
        // Do it async, then hop back to the main thread to populate the tiles.
        // ----------------------------------------------------------------------------------------
        Bukkit.getScheduler().runTaskAsynchronously(BarterContainer.INSTANCE, () -> {
            // Aggregate all-time stats across ALL stores
            AllTimeStats ats = readAllTimeStatsAcross(stores);

            // Consistency (7-day window) across ALL stores
            // Use window-aware decay so day 6 is ~10% of today's weight.
            ConsistencyScoreCalculator.Params params =
                    ConsistencyScoreCalculator.aggressiveShortWindow(7, 0.10);
            var result = computeConsistencyAcross(stores, params);

            // Back to main thread: populate items
            Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> {
                if (!player.isOnline()) return;
                Inventory top = player.getOpenInventory().getTopInventory();
                if (!(top.getHolder() instanceof Holder h)) return;
                // BASIC STALENESS CHECKS
                if (!h.getStores().equals(holder.getStores())) return;

                // Row 1 topline
                top.setItem(1, button(Material.CHEST,
                        MM("total_sales_name"),
                        ats != null ? List.of(
                                MM("total_sales_lore_units", "units", ats.totalUnits()),
                                MM("total_sales_lore_tx",    "tx",     ats.transactionCount())
                        ) : List.of(MM("total_sales_no_data"))
                ));

                top.setItem(3, button(Material.CLOCK,
                        MM("last_sale_name"),
                        ats != null && ats.lastRecord() != null ? List.of(
                                MM("last_sale_lore_item",
                                        "item",   humanMaterialName(ats.lastRecord().itemType()),
                                        "amount", ats.lastRecord().amount()),
                                MM("last_sale_lore_buyer",
                                        "buyer",  safeBuyer(ats.lastRecord())),
                                MM("last_sale_lore_abs",
                                        "abs", ABS_FMT().format(Instant.ofEpochMilli(ats.lastRecord().timestamp()))),
                                MM("last_sale_lore_since",
                                        "rel", humanizeSince(Instant.ofEpochMilli(ats.lastRecord().timestamp())))
                        ) : List.of(MM("last_sale_no_data"))
                ));

                top.setItem(5, button(Material.ENDER_EYE,
                        MM("unique_purchasers_name"),
                        ats != null
                                ? List.of(MM("unique_purchasers_lore", "count", ats.uniqueBuyerCount()))
                                : List.of(MM("unique_purchasers_no_data"))));

                top.setItem(7, button(Material.GLOW_ITEM_FRAME,
                        MM("top_item_name"),
                        ats != null && ats.topItem() != null ? List.of(
                                MM("top_item_lore_row",
                                        "item",  humanMaterialName(ats.topItem()),
                                        "units", ats.topItemUnits()),
                                MM("top_item_lore_footer")
                        ) : List.of(MM("top_item_no_data"))
                ));

                List<Component> overallLore, stabilityLore, recencyLore, trendLore;
                if (result != null) {
                    // Dynamic computed lore (kept local; all_shop_stats doesn't define per-tile score lore keys)
                    overallLore   = List.of(compGray("Score: " + pct(result.finalScore()) + "%"),
                            compGray("Blend of Stability, Recency, Trend"));
                    stabilityLore = List.of(compGray("Evenness day-to-day"),
                            compGray("Score: " + pct(result.stabilityScore()) + "%"));
                    recencyLore   = List.of(compGray("Recent activity emphasis"),
                            compGray("Score: " + pct(result.recencyScore()) + "%"));
                    trendLore     = List.of(compGray("Flat or gentle slope = better"),
                            compGray("Score: " + pct(result.trendScore()) + "%"));
                } else {
                    // Fallback text from YAML to guarantee localization
                    overallLore = stabilityLore = recencyLore = trendLore = List.of(MM("tile_lore_no_data"));
                }

                // (Tile names pulled from YAML)
                top.setItem(SLOT_OVERALL,   button(Material.NETHER_STAR, MM("overall_tile_name"),   overallLore));
                top.setItem(SLOT_STABILITY, button(Material.GREEN_DYE,    MM("stability_tile_name"), stabilityLore));
                top.setItem(SLOT_RECENCY,   button(Material.CYAN_DYE,     MM("recency_tile_name"),   recencyLore));
                top.setItem(SLOT_TREND,     button(Material.BLUE_DYE,     MM("trend_tile_name"),     trendLore));

                // Reserved (same slot as single-store)
                top.setItem(17, button(Material.PAPER,
                        MM("more_tbd_name"),
                        MML("more_tbd_lore")));

                // Back
                top.setItem(22, button(Material.BARRIER,
                        MM("back_name"),
                        MML("back_lore")));
            });
        });
    }

    // ----- Data aggregation -----

    private record AllTimeStats(
            int totalUnits,
            int transactionCount,
            Instant lastSale,
            int uniqueBuyerCount,
            Material topItem,
            int topItemUnits,
            BarterShopOwnerLogManager.TransactionRecord lastRecord
    ) {}

    private static AllTimeStats readAllTimeStatsAcross(List<BarterStore> stores) {
        int transactions = 0;
        int units = 0;

        Instant last = null;
        BarterShopOwnerLogManager.TransactionRecord lastRec = null;

        Set<UUID> buyers = new HashSet<>();
        Map<Material, Integer> byMat = new EnumMap<>(Material.class);

        try {
            for (BarterStore s : stores) {
                List<BarterShopOwnerLogManager.TransactionRecord> entries =
                        BarterShopOwnerLogManager.listAllEntries(s.getKey());
                transactions += entries.size();
                for (var r : entries) {
                    units += Math.max(0, r.amount());

                    Instant ts = Instant.ofEpochMilli(r.timestamp());
                    if (last == null || ts.isAfter(last)) {
                        last = ts;
                        lastRec = r;
                    }
                    buyers.add(r.purchaserUuid());

                    Material m = r.itemType();
                    if (m != null) byMat.merge(m, Math.max(0, r.amount()), Integer::sum);
                }
            }
        } catch (IOException e) {
            return null;
        }

        Material topMat = null;
        int topUnits = 0;
        for (var e : byMat.entrySet()) {
            if (e.getValue() > topUnits) {
                topUnits = e.getValue();
                topMat = e.getKey();
            }
        }

        return new AllTimeStats(units, transactions, last, buyers.size(), topMat, topUnits, lastRec);
    }

    private static ConsistencyScoreCalculator.Result computeConsistencyAcross(
            List<BarterStore> stores,
            ConsistencyScoreCalculator.Params params
    ) {
        try {
            List<Instant> instants = new ArrayList<>();
            for (BarterStore s : stores) {
                List<BarterShopOwnerLogManager.TransactionRecord> entries =
                        BarterShopOwnerLogManager.listAllEntries(s.getKey());
                for (var r : entries) instants.add(Instant.ofEpochMilli(r.timestamp()));
            }
            if (instants.isEmpty()) return null;

            return ConsistencyScoreCalculator.calculateFromInstants(
                    instants, params, Clock.systemUTC(), ZoneId.systemDefault()
            );
        } catch (IOException e) {
            return null;
        }
    }

    // ----- Tiny UI helpers (copied to match single-shop style) -----

    private static int pct(double unitScore) { return (int) Math.round(unitScore * 100.0); }

    private static String humanMaterialName(Material m) {
        if (m == null) return "Unknown Item";
        String s = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder sb = new StringBuilder(s.length());
        boolean cap = true;
        for (char c : s.toCharArray()) {
            if (cap && Character.isLetter(c)) { sb.append(Character.toUpperCase(c)); cap = false; }
            else sb.append(c);
            if (c == ' ') cap = true;
        }
        return sb.toString();
    }

    private static String safeBuyer(BarterShopOwnerLogManager.TransactionRecord r) {
        String b = r.purchaserName();
        if (b != null && !b.isBlank()) return b;
        UUID u = r.purchaserUuid();
        return (u != null) ? u.toString() : "Unknown";
    }

    private static String humanizeSince(Instant then) {
        Duration d = Duration.between(then, Instant.now());
        if (d.isNegative() || d.isZero()) return "just now";
        long days = d.toDays(); d = d.minusDays(days);
        long hours = d.toHours(); d = d.minusHours(hours);
        long mins = d.toMinutes();
        if (days > 0) return days + "d " + hours + "h ago";
        if (hours > 0) return hours + "h " + mins + "m ago";
        if (mins > 0) return mins + "m ago";
        return "just now";
    }

    private static ItemStack button(Material mat, Component name, List<Component> lore) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        m.displayName(name);
        if (lore != null && !lore.isEmpty()) m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(m);
        return i;
    }

    private static ItemStack pane() {
        ItemStack i = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = i.getItemMeta();
        m.displayName(Component.empty());
        i.setItemMeta(m);
        return i;
    }

    private static Component compGray(String s) { return Component.text(s, NamedTextColor.GRAY); }

    // =================================================================================================
    // Click handling for the four consistency tiles (aggregate chat messages like single-shop)
    // =================================================================================================

    public static void handleClick(Player player, int rawSlot, List<BarterStore> stores) {
        ConsistencyScoreCalculator.Params params =
                ConsistencyScoreCalculator.aggressiveShortWindow(7, 0.10);
        var result = computeConsistencyAcross(stores, params);

        if (result == null) {
            player.sendMessage(MM("msg_no_data_across"));
            return;
        }

        switch (rawSlot) {
            case SLOT_OVERALL -> sendOverallBreakdown(player, result, params);
            case SLOT_STABILITY -> sendStabilityBreakdown(player, result);
            case SLOT_RECENCY -> sendRecencyBreakdown(player, result, params);
            case SLOT_TREND -> sendTrendBreakdown(player, result, params);
            default -> { /* ignore others */ }
        }
    }

    /** Option B: convenience overload if you only have the keys (no BarterStore instances). */
    public static void handleClick(Player player, int rawSlot, Collection<BarterStoreKey> keys) {
        ConsistencyScoreCalculator.Params params =
                ConsistencyScoreCalculator.aggressiveShortWindow(7, 0.10);
        var result = computeConsistencyAcrossKeys(keys, params);

        if (result == null) {
            player.sendMessage(MM("msg_no_data_across"));
            return;
        }

        switch (rawSlot) {
            case SLOT_OVERALL -> sendOverallBreakdown(player, result, params);
            case SLOT_STABILITY -> sendStabilityBreakdown(player, result);
            case SLOT_RECENCY -> sendRecencyBreakdown(player, result, params);
            case SLOT_TREND -> sendTrendBreakdown(player, result, params);
            default -> { /* ignore */ }
        }
    }

    // ----- Chat breakdowns (mirror single-shop phrasing; scoped to "all shops") -----

    private static void sendOverallBreakdown(Player p, ConsistencyScoreCalculator.Result r,
                                             ConsistencyScoreCalculator.Params params) {
        var w = params.weights();
        double wsum = Math.max(1e-9, w.sum());
        double fs = (w.stability() * r.stabilityScore()
                + w.recency() * r.recencyScore()
                + w.trend()   * r.trendScore()) / wsum;

        p.sendMessage(MM("overall_header", "days", params.windowDays()));
        p.sendMessage(MM("overall_series", "series", fmtArray(r.dailySales())));
        p.sendMessage(MM("overall_parts",
                "stab",  pct(r.stabilityScore()),
                "rec",   pct(r.recencyScore()),
                "trend", pct(r.trendScore())));
        p.sendMessage(MM("overall_weights",
                "wstab",  pctWeight(w.stability()),
                "wrec",   pctWeight(w.recency()),
                "wtrend", pctWeight(w.trend())));
        p.sendMessage(MM("overall_final", "final", pct(fs)));
    }

    private static void sendStabilityBreakdown(Player p, ConsistencyScoreCalculator.Result r) {
        // stability = 1 / (1 + CV) with CV on log1p(daily)
        int[] d = r.dailySales();
        double[] y = dampen(d);
        double meanLog = mean(y);
        double stdLog  = stdDev(y, meanLog);
        double cv = Math.max(0.0, stdLog / (meanLog + 1e-9));
        double stability = 1.0 / (1.0 + cv);

        p.sendMessage(MM("stability_header"));
        p.sendMessage(MM("stability_series",   "series", fmtArray(d)));
        p.sendMessage(MM("stability_dampened", "series", fmtArray(y)));
        p.sendMessage(MM("stability_math",
                "mean", fmt(meanLog), "std", fmt(stdLog), "cv", fmt(cv)));
        p.sendMessage(MM("stability_final", "stability", fmt(stability)));
        p.sendMessage(MM("stability_pct",   "pct", pct(stability)));
    }

    private static void sendRecencyBreakdown(Player p, ConsistencyScoreCalculator.Result r,
                                             ConsistencyScoreCalculator.Params params) {
        // recency = decayed / total, weights w_i = e^(-λ i)
        int[] d = r.dailySales();
        double[] w = decayWeights(d.length, params.lambda());
        double decayed = 0.0;
        int total = 0;
        for (int i = 0; i < d.length; i++) {
            decayed += d[i] * w[i];
            total   += d[i];
        }
        double recency = total <= 0 ? 0.0 : decayed / total;

        p.sendMessage(MM("recency_header"));
        p.sendMessage(MM("recency_series",  "series", fmtArray(d)));
        p.sendMessage(MM("recency_weights", "lambda", fmt(params.lambda()), "weights", fmtArray(w)));
        p.sendMessage(MM("recency_math",    "sum", fmt(decayed), "total", total));
        p.sendMessage(MM("recency_final",   "recency", fmt(recency)));
        p.sendMessage(MM("recency_pct",     "pct", pct(recency)));
    }

    private static void sendTrendBreakdown(Player p, ConsistencyScoreCalculator.Result r,
                                           ConsistencyScoreCalculator.Params params) {
        // trend based on OLS slope of log1p(daily); soft-mapped with maxSlope
        int[] d = r.dailySales();
        double[] y = dampen(d);
        double meanLog = mean(y);
        double slopeLog = regressionSlope(y);
        double relSlope = (meanLog <= 0 ? 0.0 : slopeLog / (meanLog + 1e-9));
        double steepness = Math.abs(relSlope) / params.maxSlope();
        double trend = 1.0 / (1.0 + steepness);

        p.sendMessage(MM("trend_header"));
        p.sendMessage(MM("trend_dampened", "series", fmtArray(y)));
        p.sendMessage(MM("trend_math",
                "slope", fmt(slopeLog), "mean", fmt(meanLog), "relslope", fmt(relSlope)));
        p.sendMessage(MM("trend_softmap",
                "absrelslope", fmt(Math.abs(relSlope)), "maxslope", fmt(params.maxSlope()), "steepness", fmt(steepness)));
        p.sendMessage(MM("trend_final", "trend", fmt(trend)));
        p.sendMessage(MM("trend_pct",   "pct", pct(trend)));
    }

    // ----- Convenience: compute from keys if you don't have BarterStore instances -----
    private static ConsistencyScoreCalculator.Result computeConsistencyAcrossKeys(
            Collection<BarterStoreKey> keys,
            ConsistencyScoreCalculator.Params params
    ) {
        try {
            List<Instant> instants = new ArrayList<>();
            for (BarterStoreKey k : keys) {
                List<BarterShopOwnerLogManager.TransactionRecord> entries =
                        BarterShopOwnerLogManager.listAllEntries(k);
                for (var r : entries) instants.add(Instant.ofEpochMilli(r.timestamp()));
            }
            if (instants.isEmpty()) return null;

            return ConsistencyScoreCalculator.calculateFromInstants(
                    instants, params, Clock.systemUTC(), ZoneId.systemDefault()
            );
        } catch (IOException e) {
            return null;
        }
    }

    // ----- Math / formatting helpers (local copies to match single-shop behavior exactly) -----

    /** Convert a weight (0..1) to integer percent for display. */
    private static int pctWeight(double weightUnit) { return (int) Math.round(weightUnit * 100.0); }

    private static String fmt(double v) {
        return String.format(java.util.Locale.US, "%.3f", v);
    }

    private static String fmtArray(int[] a) {
        if (a.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(a[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private static String fmtArray(double[] a) {
        if (a.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(fmt(a[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    private static double[] dampen(int[] a) {
        double[] y = new double[a.length];
        for (int i = 0; i < a.length; i++) y[i] = Math.log1p(Math.max(0, a[i]));
        return y;
    }

    private static double mean(double[] a) {
        if (a.length == 0) return 0.0;
        double s = 0.0;
        for (double v : a) s += v;
        return s / a.length;
    }

    private static double stdDev(double[] a, double mean) {
        if (a.length == 0) return 0.0;
        double ss = 0.0;
        for (double v : a) {
            double d = v - mean;
            ss += d * d;
        }
        return Math.sqrt(ss / a.length);
    }

    /** Ordinary Least Squares (OLS) slope with x = 0..n-1 and y[]. */
    private static double regressionSlope(double[] y) {
        int n = y.length;
        if (n < 2) return 0.0;

        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            sumX  += i;
            sumY  += y[i];
            sumXY += i * y[i];
            sumXX += i * (double) i;
        }
        double denom = n * sumXX - sumX * sumX;
        if (denom == 0) return 0.0;
        return (n * sumXY - sumX * sumY) / denom;
    }

    private static double[] decayWeights(int n, double lambda) {
        double[] w = new double[n];
        for (int i = 0; i < n; i++) w[i] = Math.exp(-lambda * i);
        return w;
    }
}
