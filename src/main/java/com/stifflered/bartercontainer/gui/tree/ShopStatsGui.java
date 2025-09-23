package com.stifflered.bartercontainer.gui.tree;

import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.BarterShopOwnerLogManager;
import com.stifflered.bartercontainer.util.Messages;
import com.stifflered.bartercontainer.util.analytics.ConsistencyScoreCalculator;

import net.kyori.adventure.text.Component;

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
import java.util.stream.Collectors;

import static net.kyori.adventure.text.Component.text;

/**
 * "Shop Stats" GUI (27 slots), fully driven by messages.yml (MiniMessage).

 * Layout (0..26):
 *   Row 1:  [0 filler] [1 Chest] [2] [3 Clock] [4] [5 EnderEye] [6] [7 GlowFrame] [8]
 *   Row 2:  [9 NetherStar] [10] [11 GreenDye] [12] [13 CyanDye] [14] [15 BlueDye] [16] [17 Paper]
 *   Row 3:  fillers ... [22 Barrier] ...

 * Text keys live under **shop_stats.*** (no "gui." prefix).
 */
public final class ShopStatsGui {

    /** Inventory title from messages.yml. */
    private static Component title() {
        // NOTE: key is "shop_stats.title"
        return Messages.mm("shop_stats.title");
    }

    /** Absolute date/time format for the "Last Sold" label. */
    private static java.time.format.DateTimeFormatter absoluteFormatter() {
        // Delegates to config.yml (transactions.time_pattern) via TimeUtil
        return com.stifflered.bartercontainer.util.TimeUtil.absoluteFormatter();
    }

    // Public slot constants (listener uses these)
    public static final int SLOT_OVERALL   = 9;   // Nether Star
    public static final int SLOT_STABILITY = 11;  // Green Dye
    public static final int SLOT_RECENCY   = 13;  // Cyan Dye
    public static final int SLOT_TREND     = 15;  // Blue Dye

    /** Simple holder so listeners can recover the store. */
    public static final class Holder implements InventoryHolder {
        private final BarterStore store;
        private Inventory dummy;

        public Holder(BarterStore store) { this.store = Objects.requireNonNull(store, "store"); }
        public BarterStore getStore() { return store; }

        @Override public @org.jetbrains.annotations.NotNull Inventory getInventory() {
            if (dummy == null) dummy = Bukkit.createInventory(this, 9, text(" "));
            return dummy;
        }
    }

    /** Open the single-shop stats UI. */
    public static void open(Player player, BarterStore store) {
        Inventory inv = Bukkit.createInventory(new Holder(store), 27, title());

        // background
        ItemStack filler = pane();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        // all-time stats from logs
        AllTimeStats ats = readAllTimeStats(store);

        // ---- row 1 (topline) ----
        if (ats != null) {
            inv.setItem(1, button(
                    Material.CHEST,
                    Messages.mm("shop_stats.total_sales_name"),
                    List.of(
                            Messages.mm("shop_stats.total_sales_lore_units", "units", ats.totalUnits()),
                            Messages.mm("shop_stats.total_sales_lore_tx",    "tx",    ats.transactionCount())
                    )
            ));

            inv.setItem(3, button(
                    Material.CLOCK,
                    Messages.mm("shop_stats.last_sale_name"),
                    List.of(
                            Messages.mm("shop_stats.last_sale_lore_item",
                                    "item",   humanMaterialName(ats.lastRecord().itemType()),
                                    "amount", ats.lastRecord().amount()),
                            Messages.mm("shop_stats.last_sale_lore_buyer",
                                    "buyer",  ats.lastRecord().purchaserName()),
                            Messages.mm("shop_stats.last_sale_lore_abs",
                                    "abs", absoluteFormatter().format(Instant.ofEpochMilli(ats.lastRecord().timestamp()))),
                            Messages.mm("shop_stats.last_sale_lore_since",
                                    "rel", humanizeSince(Instant.ofEpochMilli(ats.lastRecord().timestamp())))
                    )
            ));

            inv.setItem(5, button(
                    Material.ENDER_EYE,
                    Messages.mm("shop_stats.unique_purchasers_name"),
                    List.of(Messages.mm("shop_stats.unique_purchasers_lore", "count", ats.uniqueBuyerCount()))
            ));

            if (ats.topItem() != null) {
                inv.setItem(7, button(
                        Material.GLOW_ITEM_FRAME,
                        Messages.mm("shop_stats.top_item_name"),
                        List.of(
                                Messages.mm("shop_stats.top_item_lore_row",
                                        "item",  humanMaterialName(ats.topItem()),
                                        "units", ats.topItemUnits()),
                                Messages.mm("shop_stats.top_item_lore_footer")
                        )
                ));
            } else {
                inv.setItem(7, button(
                        Material.GLOW_ITEM_FRAME,
                        Messages.mm("shop_stats.top_item_name"),
                        List.of(Messages.mm("shop_stats.top_item_no_data"))
                ));
            }
        } else {
            inv.setItem(1, button(Material.CHEST, Messages.mm("shop_stats.total_sales_name"),
                    List.of(Messages.mm("shop_stats.total_sales_no_data"))));
            inv.setItem(3, button(Material.CLOCK, Messages.mm("shop_stats.last_sale_name"),
                    List.of(Messages.mm("shop_stats.last_sale_no_data"))));
            inv.setItem(5, button(Material.ENDER_EYE, Messages.mm("shop_stats.unique_purchasers_name"),
                    List.of(Messages.mm("shop_stats.unique_purchasers_no_data"))));
            inv.setItem(7, button(Material.GLOW_ITEM_FRAME, Messages.mm("shop_stats.top_item_name"),
                    List.of(Messages.mm("shop_stats.top_item_no_data"))));
        }

        // ---- row 2 (consistency) ----
        ConsistencyScoreCalculator.Params params = ConsistencyScoreCalculator.Params.DEFAULT();
        var result = computeConsistency(store, params);

        if (result != null) {
            inv.setItem(SLOT_OVERALL, button(
                    Material.NETHER_STAR,
                    Messages.mm("shop_stats.overall_tile_name"),
                    List.of(
                            Messages.mm("shop_stats.overall_tile_lore_score", "score", pct(result.finalScore())),
                            Messages.mm("shop_stats.overall_tile_lore_header")
                    )
            ));
            inv.setItem(SLOT_STABILITY, button(
                    Material.GREEN_DYE,
                    Messages.mm("shop_stats.stability_tile_name"),
                    List.of(
                            Messages.mm("shop_stats.stability_tile_lore_header"),
                            Messages.mm("shop_stats.stability_tile_lore_score", "score", pct(result.stabilityScore()))
                    )
            ));
            inv.setItem(SLOT_RECENCY, button(
                    Material.CYAN_DYE,
                    Messages.mm("shop_stats.recency_tile_name"),
                    List.of(
                            Messages.mm("shop_stats.recency_tile_lore_header"),
                            Messages.mm("shop_stats.recency_tile_lore_score", "score", pct(result.recencyScore()))
                    )
            ));
            inv.setItem(SLOT_TREND, button(
                    Material.BLUE_DYE,
                    Messages.mm("shop_stats.trend_tile_name"),
                    List.of(
                            Messages.mm("shop_stats.trend_tile_lore_header"),
                            Messages.mm("shop_stats.trend_tile_lore_score", "score", pct(result.trendScore()))
                    )
            ));
        } else {
            List<Component> noData = List.of(Messages.mm("shop_stats.tile_lore_no_data"));
            inv.setItem(SLOT_OVERALL,   button(Material.NETHER_STAR, Messages.mm("shop_stats.overall_tile_name"), noData));
            inv.setItem(SLOT_STABILITY, button(Material.GREEN_DYE,  Messages.mm("shop_stats.stability_tile_name"), noData));
            inv.setItem(SLOT_RECENCY,   button(Material.CYAN_DYE,   Messages.mm("shop_stats.recency_tile_name"), noData));
            inv.setItem(SLOT_TREND,     button(Material.BLUE_DYE,   Messages.mm("shop_stats.trend_tile_name"), noData));
        }

        // reserved + back
        inv.setItem(17, button(Material.PAPER,
                Messages.mm("shop_stats.more_tbd_name"),
                Messages.mmList("shop_stats.more_tbd_lore")));

        inv.setItem(22, button(Material.BARRIER,
                Messages.mm("shop_stats.back_name"),
                Messages.mmList("shop_stats.back_lore")));

        player.openInventory(inv);
    }

    // -------- click breakdowns --------

    public static void handleClick(Player player, int rawSlot, BarterStore store) {
        ConsistencyScoreCalculator.Params params = ConsistencyScoreCalculator.Params.DEFAULT();
        var result = computeConsistency(store, params);

        if (result == null) {
            Messages.send(player, "shop_stats.msg_no_data");
            return;
        }

        switch (rawSlot) {
            case SLOT_OVERALL -> sendOverallBreakdown(player, result, params);
            case SLOT_STABILITY -> sendStabilityBreakdown(player, result);
            case SLOT_RECENCY -> sendRecencyBreakdown(player, result, params);
            case SLOT_TREND -> sendTrendBreakdown(player, result, params);
            default -> { }
        }
    }

    private static void sendOverallBreakdown(Player p, ConsistencyScoreCalculator.Result r,
                                             ConsistencyScoreCalculator.Params params) {
        var w = params.weights();
        double wsum = Math.max(1e-9, w.sum());
        double fs = (w.stability() * r.stabilityScore()
                + w.recency() * r.recencyScore()
                + w.trend()   * r.trendScore()) / wsum;

        Messages.send(p, "shop_stats.overall_header", "days", params.windowDays());
        Messages.send(p, "shop_stats.overall_series", "series", fmtArray(r.dailySales()));
        Messages.send(p, "shop_stats.overall_parts",
                "stab", pct(r.stabilityScore()),
                "rec",  pct(r.recencyScore()),
                "trend", pct(r.trendScore()));
        Messages.send(p, "shop_stats.overall_weights",
                "wstab", pctWeight(w.stability()),
                "wrec",  pctWeight(w.recency()),
                "wtrend", pctWeight(w.trend()));
        Messages.send(p, "shop_stats.overall_final", "final", pct(fs));
    }

    private static void sendStabilityBreakdown(Player p, ConsistencyScoreCalculator.Result r) {
        int[] d = r.dailySales();
        double[] y = dampen(d);
        double meanLog = mean(y);
        double stdLog  = stdDev(y, meanLog);
        double cv = Math.max(0.0, stdLog / (meanLog + 1e-9));
        double stability = 1.0 / (1.0 + cv);

        Messages.send(p, "shop_stats.stability_header");
        Messages.send(p, "shop_stats.stability_series",   "series", fmtArray(d));
        Messages.send(p, "shop_stats.stability_dampened", "series", fmtArray(y));
        Messages.send(p, "shop_stats.stability_math",
                "mean", fmt(meanLog), "std", fmt(stdLog), "cv", fmt(cv));
        Messages.send(p, "shop_stats.stability_final", "stability", fmt(stability));
        Messages.send(p, "shop_stats.stability_pct",   "pct", pct(stability));
    }

    private static void sendRecencyBreakdown(Player p, ConsistencyScoreCalculator.Result r,
                                             ConsistencyScoreCalculator.Params params) {
        int[] d = r.dailySales();
        double[] w = decayWeights(d.length, params.lambda());
        double decayed = 0.0;
        int total = 0;
        for (int i = 0; i < d.length; i++) { decayed += d[i] * w[i]; total += d[i]; }
        double recency = total <= 0 ? 0.0 : decayed / total;

        Messages.send(p, "shop_stats.recency_header");
        Messages.send(p, "shop_stats.recency_series",  "series", fmtArray(d));
        Messages.send(p, "shop_stats.recency_weights", "lambda", fmt(params.lambda()), "weights", fmtArray(w));
        Messages.send(p, "shop_stats.recency_math",    "sum", fmt(decayed), "total", total);
        Messages.send(p, "shop_stats.recency_final",   "recency", fmt(recency));
        Messages.send(p, "shop_stats.recency_pct",     "pct", pct(recency));
    }

    private static void sendTrendBreakdown(Player p, ConsistencyScoreCalculator.Result r,
                                           ConsistencyScoreCalculator.Params params) {
        int[] d = r.dailySales();
        double[] y = dampen(d);
        double meanLog = mean(y);
        double slopeLog = regressionSlope(y);
        double relSlope = (meanLog <= 0 ? 0.0 : slopeLog / (meanLog + 1e-9));
        double steepness = Math.abs(relSlope) / params.maxSlope();
        double trend = 1.0 / (1.0 + steepness);

        Messages.send(p, "shop_stats.trend_header");
        Messages.send(p, "shop_stats.trend_dampened", "series", fmtArray(y));
        Messages.send(p, "shop_stats.trend_math",
                "slope", fmt(slopeLog), "mean", fmt(meanLog), "relslope", fmt(relSlope));
        Messages.send(p, "shop_stats.trend_softmap",
                "absrelslope", fmt(Math.abs(relSlope)), "maxslope", fmt(params.maxSlope()), "steepness", fmt(steepness));
        Messages.send(p, "shop_stats.trend_final", "trend", fmt(trend));
        Messages.send(p, "shop_stats.trend_pct",   "pct", pct(trend));
    }

    // -------- data aggregation --------

    private record AllTimeStats(
            int totalUnits, int transactionCount, Instant lastSale,
            int uniqueBuyerCount, Material topItem, int topItemUnits,
            BarterShopOwnerLogManager.TransactionRecord lastRecord) {}

    private static AllTimeStats readAllTimeStats(BarterStore store) {
        try {
            List<BarterShopOwnerLogManager.TransactionRecord> entries =
                    BarterShopOwnerLogManager.listAllEntries(store.getKey());
            if (entries.isEmpty()) return null;

            int tx = entries.size();
            int units = 0;
            Instant last = null;
            BarterShopOwnerLogManager.TransactionRecord lastRec = null;

            Set<UUID> buyers = new HashSet<>();
            Map<Material, Integer> byMat = new EnumMap<>(Material.class);

            for (var r : entries) {
                units += r.amount();
                Instant ts = Instant.ofEpochMilli(r.timestamp());
                if (last == null || ts.isAfter(last)) { last = ts; lastRec = r; }
                buyers.add(r.purchaserUuid());
                Material m = r.itemType();
                if (m != null) byMat.merge(m, r.amount(), Integer::sum);
            }

            Material topMat = null; int topUnits = 0;
            for (var e : byMat.entrySet()) if (e.getValue() > topUnits) { topUnits = e.getValue(); topMat = e.getKey(); }

            return new AllTimeStats(units, tx, last, buyers.size(), topMat, topUnits, lastRec);
        } catch (IOException e) {
            return null;
        }
    }

    private static ConsistencyScoreCalculator.Result computeConsistency(
            BarterStore store, ConsistencyScoreCalculator.Params params
    ) {
        try {
            List<BarterShopOwnerLogManager.TransactionRecord> entries =
                    BarterShopOwnerLogManager.listAllEntries(store.getKey());
            if (entries.isEmpty()) return null;

            List<Instant> instants = entries.stream()
                    .map(r -> Instant.ofEpochMilli(r.timestamp()))
                    .collect(Collectors.toCollection(ArrayList::new));

            return ConsistencyScoreCalculator.calculateFromInstants(
                    instants, params, Clock.systemUTC(), ZoneId.systemDefault());
        } catch (IOException e) {
            return null;
        }
    }

    // -------- tiny UI + math helpers --------

    private static int pct(double unitScore) { return (int) Math.round(unitScore * 100.0); }
    private static int pctWeight(double weightUnit) { return (int) Math.round(weightUnit * 100.0); }
    private static String fmt(double v) { return String.format(java.util.Locale.US, "%.3f", v); }

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

    private static String humanizeSince(Instant then) {
        Duration d = Duration.between(then, Instant.now());
        if (d.isNegative() || d.isZero()) return "just now";
        long days = d.toDays(); d = d.minusDays(days);
        long hrs = d.toHours(); d = d.minusHours(hrs);
        long mins = d.toMinutes();
        if (days > 0) return days + "d " + hrs + "h ago";
        if (hrs  > 0) return hrs  + "h " + mins + "m ago";
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
        m.displayName(text(" "));
        i.setItemMeta(m);
        return i;
    }

    private static double[] dampen(int[] a) {
        double[] y = new double[a.length];
        for (int i = 0; i < a.length; i++) y[i] = Math.log1p(Math.max(0, a[i]));
        return y;
    }

    private static double mean(double[] a) {
        if (a.length == 0) return 0.0;
        double s = 0.0; for (double v : a) s += v; return s / a.length;
    }

    private static double stdDev(double[] a, double mean) {
        if (a.length == 0) return 0.0;
        double ss = 0.0; for (double v : a) { double d = v - mean; ss += d * d; }
        return Math.sqrt(ss / a.length);
    }

    /** OLS slope with x=0..n-1. */
    private static double regressionSlope(double[] y) {
        int n = y.length; if (n < 2) return 0.0;
        double sumX=0, sumY=0, sumXY=0, sumXX=0;
        for (int i=0;i<n;i++){ sumX+=i; sumY+=y[i]; sumXY+=i*y[i]; sumXX+=i*(double)i; }
        double denom = n*sumXX - sumX*sumX; if (denom == 0) return 0.0;
        return (n*sumXY - sumX*sumY) / denom;
    }

    private static double[] decayWeights(int n, double lambda) {
        double[] w = new double[n]; for (int i=0;i<n;i++) w[i] = Math.exp(-lambda * i); return w;
    }

    private static String fmtArray(int[] a) {
        if (a.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i=0;i<a.length;i++){ if(i>0) sb.append(", "); sb.append(a[i]); }
        return sb.append("]").toString();
    }

    private static String fmtArray(double[] a) {
        if (a.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i=0;i<a.length;i++){ if(i>0) sb.append(", "); sb.append(fmt(a[i])); }
        return sb.append("]").toString();
    }
}
