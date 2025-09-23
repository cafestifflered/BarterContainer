package com.stifflered.bartercontainer.gui.tree;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.store.BarterStoreKey;
import com.stifflered.bartercontainer.util.BarterShopOwnerLogManager;
import com.stifflered.bartercontainer.util.BarterShopOwnerLogManager.TransactionRecord;
import com.stifflered.bartercontainer.util.Messages;
import com.stifflered.bartercontainer.util.skin.HeadService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
import java.time.format.DateTimeFormatter;

import java.util.*;
import java.util.stream.Collectors;


/**
 * "Buyer History" top-level menu (Large Chest, 54 slots).

 * Purpose:
 *  - Shows one player head per unique purchaser of this store (across all time).
 *  - Bottom row contains: ← Prev (red tipped), ⛔ Back, 🥇 Top Purchaser (slot 47), 📖 All Purchases (written book), → Next (green tipped).
 *  - Clicking a head opens {@link BuyerHistoryDetailGui} for that purchaser.

 * Data:
 *  - Loaded asynchronously from {@link BarterShopOwnerLogManager#listAllEntries(BarterStoreKey)}.
 *  - "Top Purchaser" is defined as the buyer with the most transactions; ties broken by
 *    total items purchased; final tie-breaker = most recent purchase.

 * Integration:
 *  - Like {@link LogBookHubGui}, this class does not register a listener. Your central
 *    BarterInventoryListener should cancel clicks and route actions by inspecting the
 *    {@link Holder} marker and slots.

 * Skins/Heads:
 *  - Uses {@link HeadService} to ensure heads render for Java AND Bedrock (Geyser/Floodgate).
 *    We place an immediate skull, then asynchronously refresh the texture if needed (MineSkin).

 * I18n:
 *  - Pulls titles, button names, and lore from messages.yml:
 *      gui.tree.all_buyer_history.*
 *    (Including nav labels, head lore, top purchaser, and the "All Purchases" book.)
 */
public final class BuyerHistoryGui {

    /** Title for the inventory (owner-sensitive). */
    private static Component title(BarterStore store) {
        String owner = store.getPlayerProfile() != null ? Objects.toString(store.getPlayerProfile().getName(), "") : "";
        if (owner.isBlank()) return Messages.mm("gui.tree.buyer_history.title_ownerless");
        return Messages.mm("gui.tree.buyer_history.title_with_owner", "owner", owner);
    }

    /** Fixed layout constants. */
    public static final int SIZE = 54;
    private static final int CONTENT_SLOTS = 45; // 0..44 reserved for heads
    public static final int SLOT_PREV = 45;
    public static final int SLOT_BACK = 49;
    public static final int SLOT_TOP_BUYER = 47;     // stable position
    public static final int SLOT_ALL_PURCHASES = 51;
    public static final int SLOT_NEXT = 53;

    /** Date label for lore (newest purchase date shown on heads). */
    private static DateTimeFormatter DATE() {
        return com.stifflered.bartercontainer.util.TimeUtil.absoluteFormatter();
    }

    // --------------------------------------------------------------------------------------------
    // HeadService accessor: use the single instance owned by the plugin.
    // Avoids constructing multiple HeadService objects and reusing disk cache cleanly.
    // --------------------------------------------------------------------------------------------
    private static HeadService heads() {
        return BarterContainer.INSTANCE.getHeadService();
    }

    /**
     * Holder marker for identification + routing.
     * Carries the store context and current page for pagination.

     * Publishes `pages` so the central listener can block invalid next/prev
     * without reopening the GUI (prevents visual "blink").
     */
    public static final class Holder implements InventoryHolder {
        private final BarterStore store;
        private final int page;
        private volatile int pages = 1; // published page count
        private Inventory inventory; // attached after create

        public Holder(BarterStore store, int page) {
            this.store = Objects.requireNonNull(store, "store");
            this.page = Math.max(0, page);
        }
        public BarterStore getStore() { return store; }
        public int getPage() { return page; }
        public int getPages() { return pages; }
        void setPages(int pages) { this.pages = Math.max(1, pages); }

        void attach(Inventory inv) { this.inventory = inv; }

        @Override
        public @NotNull Inventory getInventory() {
            if (inventory == null) {
                inventory = Bukkit.createInventory(this, SIZE, title(store));
            }
            return inventory;
        }
    }

    /**
     * Open the Buyer History menu at the requested page (0-indexed).
     * Loads data off-thread; UI updates happen on main thread.

     * Change: no "re-open with clamped page". We compute total pages, store them on
     * the holder, and render. Your listener must check holder.getPages() to prevent
     * paging past ends — which avoids any blink.
     */
    public static void open(Player player, BarterStore store, int page) {
        Holder holder = new Holder(store, page);
        Inventory inv = Bukkit.createInventory(holder, SIZE, title(store));
        holder.attach(inv);

        // Initial frame (placeholders) so the GUI appears immediately.
        drawChrome(inv, null, page, 1);
        player.openInventory(inv);

        // Load + build contents async
        Bukkit.getScheduler().runTaskAsynchronously(BarterContainer.INSTANCE, () -> {
            Aggregates aggr = aggregate(store.getKey());

            // ------------------------------------------------------------------------------------
            // PRE-WARM: Ask HeadService to ensure Bedrock skins are cached for the buyers
            // we are about to render (first page worth is enough, but warming all is OK).
            // This runs off-thread and will not block the UI.
            // ------------------------------------------------------------------------------------
            try {
                HeadService hs = heads();
                if (hs != null) {
                    // Warm up to one page worth for fast first render.
                    // (We still warm top purchaser explicitly.)
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
                // Never let warmup failures impact GUI flow
            }
            // ------------------------------------------------------------------------------------

            Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> {
                if (!player.isOnline()) return;

                int pages = Math.max(1, (int) Math.ceil(uniqueBuyersForGrid(aggr).size() / (double) CONTENT_SLOTS));

                Inventory top = player.getOpenInventory().getTopInventory();
                if (!(top.getHolder() instanceof Holder h)) return;
                if (!java.util.Objects.equals(h.getStore().getKey().key(), store.getKey().key())) return;
                if (h.getPage() != page) return;

                // Publish page count (listener uses this to block invalid nav)
                h.setPages(pages);

                // Render (fillHeads clamps internally)
                fillHeads(top, aggr, page);
                drawChrome(top, aggr, page, pages);
            });
        });
    }

    /* ------------------------------- Rendering ------------------------------- */

    /**
     * Draw bottom row controls with real tipped arrows:
     *  - Prev  = Arrow of Healing (red)
     *  - Next  = Arrow of Luck (green)
     *  - Back  = Barrier (text from messages.yml)
     *  - Top Purchaser head lives in slot 47 (always visible if known)
     *  - All Purchases = WRITTEN_BOOK
     */
    private static void drawChrome(Inventory inv, Aggregates aggr, int page, int totalPages) {
        // Fill bottom with panes first (for clean look)
        ItemStack pane = pane();
        for (int slot = 45; slot < 54; slot++) inv.setItem(slot, pane);

        // ← Previous Page (Arrow of Healing = red)
        ItemStack prev = arrow(PotionType.HEALING);
        named(prev,
                Messages.mm("gui.tree.all_buyer_history.nav_prev_name"),
                List.of(Messages.mm("gui.tree.all_buyer_history.nav_page_label", "page", page + 1, "pages", Math.max(1, totalPages)))
        );
        inv.setItem(SLOT_PREV, prev);

        // ⛔ Back (to hub)
        inv.setItem(SLOT_BACK, backToHubButton());

        // 🥇 Top Purchaser (always in slot 47)
        if (aggr != null && aggr.topPurchaser != null) {
            UUID topUuid = aggr.topPurchaser;
            String name = aggr.names.getOrDefault(topUuid, "Unknown");
            int tx = aggr.countByBuyer.getOrDefault(topUuid, 0);
            int items = aggr.itemsByBuyer.getOrDefault(topUuid, 0);
            long lastTs = aggr.lastTsByBuyer.getOrDefault(topUuid, 0L);

            // Build "purple title" + "yellow buyer name as first lore line"
            Component purpleTitle = Messages.mm("gui.tree.all_buyer_history.top_purchaser_name");
            List<Component> loreBlock = new ArrayList<>();
            loreBlock.add(displayHeadName(name)); // yellow buyer name as requested
            loreBlock.addAll(Messages.mmList("gui.tree.all_buyer_history.top_purchaser_lore",
                    "tx", tx,
                    "items", items,
                    "last", (lastTs == 0L ? "—" : DATE().format(Instant.ofEpochMilli(lastTs)))));

            // Build an immediate skull and schedule an async Bedrock texture refresh if needed
            ItemStack headImmediate = heads().buildHeadAsync(
                    topUuid,
                    name,
                    updated -> {
                        // Guard: only update if this inventory is still alive and still our Holder.
                        if (!(inv.getHolder() instanceof Holder)) return;
                        inv.setItem(
                                SLOT_TOP_BUYER,
                                withBuyerPdc(
                                        decoratedHead(
                                                updated,
                                                purpleTitle,
                                                loreBlock
                                        ),
                                        topUuid
                                )
                        );
                    }
            );
            // Set initial label/lore on the immediate head
            inv.setItem(
                    SLOT_TOP_BUYER,
                    withBuyerPdc(
                            decoratedHead(
                                    headImmediate,
                                    purpleTitle,
                                    loreBlock
                            ),
                            topUuid
                    )
            );
        } else {
            inv.setItem(SLOT_TOP_BUYER,
                    button(Material.PLAYER_HEAD,
                            Messages.mm("gui.tree.all_buyer_history.top_purchaser_name"),
                            Messages.mmList("gui.tree.all_buyer_history.top_purchaser_empty_lore")));
        }

        // 📖 All Purchases (written book)
        inv.setItem(
                SLOT_ALL_PURCHASES,
                button(Material.WRITTEN_BOOK,
                        Messages.mm("gui.tree.all_buyer_history.all_purchases_button_name"),
                        Messages.mmList("gui.tree.all_buyer_history.all_purchases_button_lore"))
        );

        // → Next Page (Arrow of Luck = green)
        ItemStack next = arrow(PotionType.LUCK);
        named(next,
                Messages.mm("gui.tree.all_buyer_history.nav_next_name"),
                List.of(Messages.mm("gui.tree.all_buyer_history.nav_page_label", "page", page + 1, "pages", Math.max(1, totalPages)))
        );
        inv.setItem(SLOT_NEXT, next);
    }

    private static void fillHeads(Inventory inv, Aggregates aggr, int page) {
        // Clear content area
        for (int i = 0; i < CONTENT_SLOTS; i++) inv.setItem(i, null);
        List<UUID> gridBuyers = uniqueBuyersForGrid(aggr);
        if (gridBuyers.isEmpty()) return;

        // Clamp to avoid subList exceptions
        int size = gridBuyers.size();
        int pages = Math.max(1, (int) Math.ceil(size / (double) CONTENT_SLOTS));
        int safePage = Math.max(0, Math.min(page, pages - 1));

        int from = safePage * CONTENT_SLOTS;
        if (from >= size) return;
        int to = Math.min(from + CONTENT_SLOTS, size);
        List<UUID> slice = gridBuyers.subList(from, to);

        for (int idx = 0; idx < slice.size(); idx++) {
            final int slot = idx; // capture for lambda
            UUID uuid = slice.get(idx);
            String name = aggr.names.getOrDefault(uuid, "Unknown");
            int tx = aggr.countByBuyer.getOrDefault(uuid, 0);
            int items = aggr.itemsByBuyer.getOrDefault(uuid, 0);
            long last = aggr.lastTsByBuyer.getOrDefault(uuid, 0L);

            List<Component> lore = Messages.mmList("gui.tree.all_buyer_history.head_lore",
                    "tx", tx,
                    "items", items,
                    "last", (last == 0L ? "—" : DATE().format(Instant.ofEpochMilli(last))));

            // Build immediate head; HeadService will call back with a refreshed skull if needed
            ItemStack immediate = heads().buildHeadAsync(
                    uuid,
                    name,
                    updated -> {
                        if (!(inv.getHolder() instanceof Holder)) return; // still this GUI?
                        inv.setItem(slot, withBuyerPdc(decoratedHead(updated, headNameFor(name), lore), uuid));
                    }
            );
            inv.setItem(slot, withBuyerPdc(decoratedHead(immediate, headNameFor(name), lore), uuid));
        }
    }

    /* ------------------------------- Data ----------------------------------- */

    private static Aggregates aggregate(BarterStoreKey key) {
        List<TransactionRecord> all;
        try {
            all = BarterShopOwnerLogManager.listAllEntries(key);
        } catch (Exception e) {
            return new Aggregates(); // empty
        }

        Map<UUID, Integer> count = new HashMap<>();
        Map<UUID, Integer> items = new HashMap<>();
        Map<UUID, Long> last = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();

        for (TransactionRecord r : all) {
            UUID u = r.purchaserUuid();
            count.merge(u, 1, Integer::sum);
            items.merge(u, Math.max(0, r.amount()), Integer::sum);
            last.merge(u, r.timestamp(), Math::max);
            if (r.purchaserName() != null && !r.purchaserName().isBlank()) {
                names.put(u, r.purchaserName());
            }
        }

        // Order: most purchases desc, then most items desc, then most recent purchase desc
        List<UUID> ordered = count.keySet().stream()
                .sorted((a, b) -> {
                    int c = Integer.compare(count.getOrDefault(b, 0), count.getOrDefault(a, 0));
                    if (c != 0) return c;
                    int i = Integer.compare(items.getOrDefault(b, 0), items.getOrDefault(a, 0));
                    if (i != 0) return i;
                    return Long.compare(last.getOrDefault(b, 0L), last.getOrDefault(a, 0L));
                })
                .collect(Collectors.toList());

        UUID top = ordered.isEmpty() ? null : ordered.get(0);

        Aggregates aggr = new Aggregates();
        aggr.countByBuyer = count;
        aggr.itemsByBuyer = items;
        aggr.lastTsByBuyer = last;
        aggr.names = names;
        aggr.orderedBuyers = ordered;
        aggr.topPurchaser = top;
        return aggr;
    }

    /** Mutable bag for computed aggregates (kept private to this class). */
    private static final class Aggregates {
        Map<UUID, Integer> countByBuyer = new HashMap<>();
        Map<UUID, Integer> itemsByBuyer = new HashMap<>();
        Map<UUID, Long>    lastTsByBuyer = new HashMap<>();
        Map<UUID, String>  names = new HashMap<>();
        List<UUID> orderedBuyers = new ArrayList<>();
        UUID topPurchaser;
    }

    /* ------------------------------- Items ---------------------------------- */

    /** Blank pane. */
    private static ItemStack pane() {
        ItemStack i = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = i.getItemMeta();
        m.displayName(Component.empty());
        i.setItemMeta(m);
        return i;
    }

    /** Generic button item with name + lore, and hidden attributes. */
    private static ItemStack button(Material m, Component name, List<Component> lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta im = i.getItemMeta();
        im.displayName(name.colorIfAbsent(NamedTextColor.AQUA));
        if (lore != null && !lore.isEmpty()) im.lore(lore);
        im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(im);
        return i;
    }

    /**
     * (Legacy helper) Create a player head item for the given UUID + name + lore using Bukkit only.
     * Kept for reference; not used by the main flow since we now rely on HeadService for Bedrock safety.
     */
    @SuppressWarnings("unused")
    private static ItemStack playerHeadLegacy(UUID uuid, String name, List<Component> lore) {
        ItemStack i = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) i.getItemMeta();
        sm.displayName(displayHeadName(name));
        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid); // safe offline
        sm.setOwningPlayer(off);
        if (lore != null && !lore.isEmpty()) sm.lore(lore);
        sm.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(sm);
        return i;
    }

    /** Decorate a (possibly refreshed) skull with name + lore + hidden flags. */
    private static ItemStack decoratedHead(ItemStack skull, Component name, List<Component> lore) {
        ItemMeta base = skull.getItemMeta();
        if (base instanceof SkullMeta sm) {
            sm.displayName(name.colorIfAbsent(NamedTextColor.YELLOW));
            if (lore != null && !lore.isEmpty()) sm.lore(lore);
            sm.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            skull.setItemMeta(sm);
        }
        return skull;
    }

    /** Create a tipped arrow using modern API (no deprecated PotionData). */
    private static ItemStack arrow(PotionType type) {
        ItemStack i = new ItemStack(Material.TIPPED_ARROW);
        PotionMeta pm = (PotionMeta) i.getItemMeta();
        pm.setBasePotionType(type);
        pm.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        i.setItemMeta(pm);
        return i;
    }

    /** Assign name + lore to an existing item (Component-aware). */
    private static void named(ItemStack i, Component name, List<Component> lore) {
        ItemMeta im = i.getItemMeta();
        im.displayName(name.colorIfAbsent(NamedTextColor.AQUA));
        if (lore != null && !lore.isEmpty()) im.lore(lore);
        i.setItemMeta(im);
    }

    /** Back-to-hub button using messages.yml (All Barrels verbiage in this key). */
    private static ItemStack backToHubButton() {
        ItemStack i = new ItemStack(Material.BARRIER);
        ItemMeta im = i.getItemMeta();
        im.displayName(Messages.mm("gui.tree.all_buyer_history.back_to_hub_name"));
        im.lore(Messages.mmList("gui.tree.all_buyer_history.back_to_hub_lore"));
        im.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        i.setItemMeta(im);
        return i;
    }

    // --- PDC helper to stamp buyer UUID on a head (so click handler can read it reliably)
    private static ItemStack withBuyerPdc(ItemStack skull, UUID buyerUuid) {
        ItemMeta im = skull.getItemMeta();
        var pdc = im.getPersistentDataContainer();
        pdc.set(new org.bukkit.NamespacedKey(BarterContainer.INSTANCE, "buyer_uuid"),
                org.bukkit.persistence.PersistentDataType.STRING,
                buyerUuid.toString());
        skull.setItemMeta(im);
        return skull;
    }

    /** Head display name from messages.yml template. */
    private static Component headNameFor(String buyerName) {
        return Messages.mm("gui.tree.all_buyer_history.head_name", "buyer", buyerName == null || buyerName.isBlank() ? "Unknown" : buyerName);
    }

    /** Yellow name used when decorating heads via legacy helper. */
    private static Component displayHeadName(String buyerName) {
        // Using the same template ensures consistency if this legacy path is ever used.
        return headNameFor(buyerName).color(NamedTextColor.YELLOW);
    }

    /** Buyers to render in the 0..44 grid, with duplicates removed (order preserved). */
    private static List<UUID> uniqueBuyersForGrid(Aggregates aggr) {
        if (aggr == null || aggr.orderedBuyers == null || aggr.orderedBuyers.isEmpty()) return List.of();
        // LinkedHashSet preserves insertion order while removing dups
        return new ArrayList<>(new LinkedHashSet<>(aggr.orderedBuyers));
    }
}
