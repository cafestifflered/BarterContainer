package com.stifflered.bartercontainer.gui.tree;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.store.BarterStoreKey;
import com.stifflered.bartercontainer.util.BarterShopOwnerLogManager;
import com.stifflered.bartercontainer.util.BarterShopOwnerLogManager.TransactionRecord;
import com.stifflered.bartercontainer.util.Messages;
import com.stifflered.bartercontainer.util.skin.HeadService;

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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;

import java.util.*;
import java.util.stream.Collectors;

import static net.kyori.adventure.text.Component.text;

/**
 * "All Buyer History" (Large Chest, 54).
 * One head per unique purchaser across *all* provided store keys.
 * Bottom row: ← Prev (45), 🥇 Top Purchaser (47), ⛔ Back (49), 📖 All Purchases (51), → Next (53).
 */
public final class AllBuyerHistoryGui {

    /* ------------------------------------------------------------------------
     * Title now comes from messages.yml:
     *   all_buyer_history.title_ownerless / title_with_owner
     * --------------------------------------------------------------------- */
    private static Component title(String ownerLabel) {
        return (ownerLabel == null || ownerLabel.isBlank())
                ? Messages.mm("gui.tree.all_buyer_history.title_ownerless")
                : Messages.mm("gui.tree.all_buyer_history.title_with_owner", "owner", ownerLabel);
    }

    public static final int SIZE = 54;
    private static final int CONTENT_SLOTS = 45;
    private static final int SLOT_PREV = 45, SLOT_TOP_BUYER = 47, SLOT_BACK = 49, SLOT_ALL_PURCHASES = 51, SLOT_NEXT = 53;

    // Centralized time formatting (pattern/zone from config via TimeUtil)
    private static java.time.format.DateTimeFormatter DATE() {
        return com.stifflered.bartercontainer.util.TimeUtil.absoluteFormatter();
    }

    private static HeadService heads() {
        return BarterContainer.INSTANCE.getHeadService();
    }

    /** Holder carries key set + owner label + page + published total pages. */
    public static final class Holder implements InventoryHolder {
        private final Set<BarterStoreKey> keys;
        private final String ownerLabel;
        private final BarterStoreKey originKey;
        private final int page;
        private volatile int pages = 1;
        private Inventory inv;
        public Holder(Collection<BarterStoreKey> keys, String ownerLabel, BarterStoreKey originKey, int page) {
            this.keys = Set.copyOf(keys);
            this.ownerLabel = ownerLabel;
            this.originKey = originKey;
            this.page = Math.max(0, page);
        }
        public Set<BarterStoreKey> getKeys() { return keys; }
        public String getOwnerLabel() { return ownerLabel; }
        public BarterStoreKey getOriginKey() { return originKey; }
        public int getPage() { return page; }
        public int getPages() { return pages; }
        void setPages(int p) { this.pages = Math.max(1, p); }
        void attach(Inventory i) { this.inv = i; }
        @Override public @NotNull Inventory getInventory() {
            if (inv == null) inv = Bukkit.createInventory(this, SIZE, title(ownerLabel));
            return inv;
        }
    }

    public static void open(Player player, Collection<BarterStoreKey> keys, String ownerLabel, BarterStoreKey originKey, int page) {
        Holder holder = new Holder(keys, ownerLabel, originKey, page);
        Inventory inv = Bukkit.createInventory(holder, SIZE, title(ownerLabel));
        holder.attach(inv);

        drawChrome(inv, null, page, 1);
        player.openInventory(inv);

        Bukkit.getScheduler().runTaskAsynchronously(BarterContainer.INSTANCE, () -> {
            Aggregates aggr = aggregate(keys);

            // ------------------------------------------------------------------------------------
            // PRE-WARM: ensure Bedrock buyers that will be rendered (first page is enough)
            // are cached in HeadService; also warm the top purchaser explicitly.
            // This runs off-thread and is safe to ignore on failure.
            // ------------------------------------------------------------------------------------
            try {
                HeadService hs = heads();
                if (hs != null) {
                    Set<UUID> distinct = new LinkedHashSet<>(aggr.orderedBuyers); // never null
                    int limit = Math.min(distinct.size(), CONTENT_SLOTS);
                    int i = 0;
                    for (UUID u : distinct) {
                        hs.ensureCachedBedrock(u); // no-op for Java players
                        if (++i >= limit) break;
                    }
                    if (aggr.topPurchaser != null) {
                        hs.ensureCachedBedrock(aggr.topPurchaser);
                    }
                }
            } catch (Throwable ignored) {
                // Don't fail GUI if warmup has issues
            }
            // ------------------------------------------------------------------------------------

            Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> {
                if (!player.isOnline()) return;
                Inventory top = player.getOpenInventory().getTopInventory();
                if (!(top.getHolder() instanceof Holder h)) return;
                if (h.getPage() != page) return;
                if (!h.getKeys().equals(Set.copyOf(keys))) return;
                if (!Objects.equals(h.getOriginKey(), originKey)) return;

                int pages = Math.max(1, (int) Math.ceil(uniqueBuyersForGrid(aggr).size() / (double) CONTENT_SLOTS));
                h.setPages(pages);

                fillHeads(top, aggr, page);
                drawChrome(top, aggr, page, pages);
            });
        });
    }

    /* ------------------------------- Aggregation ------------------------------- */

    private static Aggregates aggregate(Collection<BarterStoreKey> keys) {
        Map<UUID, Integer> count = new HashMap<>();
        Map<UUID, Integer> items = new HashMap<>();
        Map<UUID, Long>    last  = new HashMap<>();
        Map<UUID, String>  names = new HashMap<>();
        try {
            for (BarterStoreKey k : keys) {
                List<TransactionRecord> all = BarterShopOwnerLogManager.listAllEntries(k);
                for (TransactionRecord r : all) {
                    UUID u = r.purchaserUuid();
                    count.merge(u, 1, Integer::sum);
                    items.merge(u, Math.max(0, r.amount()), Integer::sum);
                    last.merge(u, r.timestamp(), Math::max);
                    if (r.purchaserName() != null && !r.purchaserName().isBlank()) names.put(u, r.purchaserName());
                }
            }
        } catch (Exception ignored) {}

        List<UUID> ordered = count.keySet().stream()
                .sorted((a,b)->{
                    int c = Integer.compare(count.getOrDefault(b,0), count.getOrDefault(a,0));
                    if (c!=0) return c;
                    int i = Integer.compare(items.getOrDefault(b,0), items.getOrDefault(a,0));
                    if (i!=0) return i;
                    return Long.compare(last.getOrDefault(b,0L), last.getOrDefault(a,0L));
                })
                .collect(Collectors.toList());

        Aggregates ag = new Aggregates();
        ag.countByBuyer = count;
        ag.itemsByBuyer = items;
        ag.lastTsByBuyer = last;
        ag.names = names;
        ag.orderedBuyers = ordered;
        ag.topPurchaser = ordered.isEmpty() ? null : ordered.get(0);
        return ag;
    }

    private static final class Aggregates {
        Map<UUID,Integer> countByBuyer = new HashMap<>();
        Map<UUID,Integer> itemsByBuyer = new HashMap<>();
        Map<UUID,Long>    lastTsByBuyer = new HashMap<>();
        Map<UUID,String>  names = new HashMap<>();
        List<UUID> orderedBuyers = new ArrayList<>();
        UUID topPurchaser;
    }

    /* ------------------------------- Rendering -------------------------------- */

    /* ------------------------------------------------------------------------
     * Chrome (nav / back / top-buyer / all-purchases) now uses messages.yml:
     *   all_buyer_history.nav_prev_name / nav_next_name / nav_page_label
     *   all_buyer_history.back_to_hub_name / back_to_hub_lore
     *   all_buyer_history.top_purchaser_name / top_purchaser_lore / top_purchaser_empty_lore
     *   all_buyer_history.all_purchases_button_name / all_purchases_button_lore
     * --------------------------------------------------------------------- */
    private static void drawChrome(Inventory inv, Aggregates aggr, int page, int totalPages) {
        ItemStack pane = pane();
        for (int slot = 45; slot < 54; slot++) inv.setItem(slot, pane);

        // ← Prev
        ItemStack prev = arrow(PotionType.HEALING);
        Component prevName = Messages.mm("gui.tree.all_buyer_history.nav_prev_name");
        List<Component> prevLore = List.of(
                Messages.mm("gui.tree.all_buyer_history.nav_page_label", "page", page + 1, "pages", Math.max(1, totalPages))
        );
        named(prev, prevName, prevLore);
        inv.setItem(SLOT_PREV, prev);

        // 🥇 Top Purchaser (head item with special lore)
        if (aggr != null && aggr.topPurchaser != null) {
            UUID topBuyer = aggr.topPurchaser;
            String buyerName = aggr.names.getOrDefault(topBuyer, "Unknown");
            int tx = aggr.countByBuyer.getOrDefault(topBuyer, 0);
            int items = aggr.itemsByBuyer.getOrDefault(topBuyer, 0);
            long last = aggr.lastTsByBuyer.getOrDefault(topBuyer, 0L);
            String lastStr = (last == 0L ? "—" : DATE().format(Instant.ofEpochMilli(last)));

            // Purple name (item display) + yellow buyer name as FIRST lore line
            Component headDisplayName = Messages.mm("gui.tree.all_buyer_history.top_purchaser_name");
            List<Component> lore = new ArrayList<>();
            lore.add(Messages.mm("gui.tree.all_buyer_history.head_name", "buyer", buyerName).color(NamedTextColor.YELLOW));
            lore.addAll(Messages.mmList("gui.tree.all_buyer_history.top_purchaser_lore",
                    "tx", tx, "items", items, "last", lastStr));

            ItemStack immediate = heads().buildHeadAsync(topBuyer, buyerName, updated -> {
                if (!(inv.getHolder() instanceof Holder)) return;
                inv.setItem(SLOT_TOP_BUYER, withBuyerPdc(decoratedHead(updated, headDisplayName, lore), topBuyer));
            });
            inv.setItem(SLOT_TOP_BUYER, withBuyerPdc(decoratedHead(immediate, headDisplayName, lore), topBuyer));
        } else {
            ItemStack placeholder = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta pim = placeholder.getItemMeta();
            pim.displayName(Messages.mm("gui.tree.all_buyer_history.top_purchaser_name"));
            pim.lore(Messages.mmList("gui.tree.all_buyer_history.top_purchaser_empty_lore"));
            pim.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            placeholder.setItemMeta(pim);
            inv.setItem(SLOT_TOP_BUYER, placeholder);
        }

        // ⛔ Back
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Messages.mm("gui.tree.all_buyer_history.back_to_hub_name"));
        backMeta.lore(Messages.mmList("gui.tree.all_buyer_history.back_to_hub_lore"));
        backMeta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        back.setItemMeta(backMeta);
        inv.setItem(SLOT_BACK, back);

        // 📖 All Purchases
        ItemStack allPurchases = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta apm = allPurchases.getItemMeta();
        apm.displayName(Messages.mm("gui.tree.all_buyer_history.all_purchases_button_name"));
        apm.lore(Messages.mmList("gui.tree.all_buyer_history.all_purchases_button_lore"));
        apm.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        allPurchases.setItemMeta(apm);
        inv.setItem(SLOT_ALL_PURCHASES, allPurchases);

        // → Next
        ItemStack next = arrow(PotionType.LUCK);
        Component nextName = Messages.mm("gui.tree.all_buyer_history.nav_next_name");
        List<Component> nextLore = List.of(
                Messages.mm("gui.tree.all_buyer_history.nav_page_label", "page", page + 1, "pages", Math.max(1, totalPages))
        );
        named(next, nextName, nextLore);
        inv.setItem(SLOT_NEXT, next);
    }

    private static void fillHeads(Inventory inv, Aggregates aggr, int page) {
        for (int i = 0; i < CONTENT_SLOTS; i++) inv.setItem(i, null);
        List<UUID> gridBuyers = uniqueBuyersForGrid(aggr);
        if (gridBuyers.isEmpty()) return;

        int size = gridBuyers.size();
        int pages = Math.max(1, (int) Math.ceil(size / (double) CONTENT_SLOTS));
        int safePage = Math.max(0, Math.min(page, pages - 1));

        int from = safePage * CONTENT_SLOTS;
        if (from >= size) return;
        int to = Math.min(from + CONTENT_SLOTS, size);
        List<UUID> slice = gridBuyers.subList(from, to);

        for (int idx = 0; idx < slice.size(); idx++) {
            final int slot = idx;
            UUID u = slice.get(idx);
            String buyerName = aggr.names.getOrDefault(u, "Unknown");
            int tx = aggr.countByBuyer.getOrDefault(u, 0);
            int items = aggr.itemsByBuyer.getOrDefault(u, 0);
            long last = aggr.lastTsByBuyer.getOrDefault(u, 0L);
            String lastStr = (last == 0L ? "—" : DATE().format(Instant.ofEpochMilli(last)));

            Component headDisplayName = Messages.mm("gui.tree.all_buyer_history.head_name", "buyer", buyerName);
            List<Component> lore = Messages.mmList("gui.tree.all_buyer_history.head_lore",
                    "tx", tx, "items", items, "last", lastStr);

            ItemStack immediate = heads().buildHeadAsync(u, buyerName, updated -> {
                if (!(inv.getHolder() instanceof Holder)) return;
                inv.setItem(slot, withBuyerPdc(decoratedHead(updated, headDisplayName, lore), u));
            });
            inv.setItem(slot, withBuyerPdc(decoratedHead(immediate, headDisplayName, lore), u));
        }
    }

    /* ------------------------------- Items ------------------------------------ */

    private static ItemStack pane() { ItemStack i=new ItemStack(Material.GRAY_STAINED_GLASS_PANE); ItemMeta m=i.getItemMeta(); m.displayName(Component.empty()); i.setItemMeta(m); return i; }

    private static ItemStack decoratedHead(ItemStack skull, Component displayName, List<Component> lore) {
        ItemMeta base = skull.getItemMeta();
        if (base instanceof SkullMeta sm) {
            sm.displayName(displayName != null ? displayName : text("Unknown", NamedTextColor.YELLOW));
            if (lore != null && !lore.isEmpty()) sm.lore(lore);
            sm.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            skull.setItemMeta(sm);
        }
        return skull;
    }

    private static ItemStack arrow(PotionType type) {
        ItemStack i = new ItemStack(Material.TIPPED_ARROW);
        PotionMeta pm = (PotionMeta) i.getItemMeta();
        pm.setBasePotionType(type);
        pm.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        i.setItemMeta(pm);
        return i;
    }

    private static void named(ItemStack i, Component name, List<Component> lore) {
        ItemMeta im = i.getItemMeta();
        im.displayName(name);
        if (lore != null && !lore.isEmpty()) im.lore(lore);
        im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(im);
    }

    private static ItemStack withBuyerPdc(ItemStack skull, UUID buyerUuid) {
        ItemMeta im = skull.getItemMeta();
        var pdc = im.getPersistentDataContainer();
        pdc.set(new org.bukkit.NamespacedKey(BarterContainer.INSTANCE, "buyer_uuid"),
                org.bukkit.persistence.PersistentDataType.STRING,
                buyerUuid.toString());
        skull.setItemMeta(im);
        return skull;
    }

    /** Buyers to render in the 0..44 grid, with duplicates removed (order preserved). */
    private static List<UUID> uniqueBuyersForGrid(Aggregates aggr) {
        if (aggr == null || aggr.orderedBuyers == null || aggr.orderedBuyers.isEmpty()) return List.of();
        // LinkedHashSet preserves insertion order while removing dups
        return new ArrayList<>(new LinkedHashSet<>(aggr.orderedBuyers));
    }
}
