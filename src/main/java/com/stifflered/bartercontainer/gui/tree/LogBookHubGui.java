package com.stifflered.bartercontainer.gui.tree;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.BarterShopOwnerLogManager;
import com.stifflered.bartercontainer.util.BarterShopOwnerLogManager.TransactionRecord;
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

import org.jetbrains.annotations.NotNull;

import java.util.*;


/**
 * Minimal 9-slot "Logs & Analytics" hub (single barrel).

 * Responsibilities:
 *  - Render the landing menu that replaces the direct "list of transactions" view.
 *  - Provide 5 icon "destinations": Back, This Shop's Stats, Top Selling Items,
 *    Buyer History, All Barrels Mode.
 *  - Open as a read-only/inert inventory; clicks are centrally cancelled by BarterInventoryListener,
 *    just like BarterGui/BarterBuyGui.

 * UX / Layout:
 *  Index: 0   1   2   3   4   5   6   7   8
 *         ⛔  ░  🛢  ░  🥇 ░  🧭 ░  🛎
 * Items:
 *   0 = Barrier      → "Back to Main Menu" (handled by listener; routes back to main GUI)
 *   2 = Barrel       → "This Shop's Stats"
 *   4 = Gold Ingot   → "Top Selling Items" (dynamic lore from logs)
 *   6 = Compass      → "Buyer History"
 *   8 = Beacon       → "All Barrels Mode"
 *   1,3,5,7 = Gray panes as visual separators

 * Integration Notes:
 *  - This class does NOT implement Listener and is NOT registered in onEnable(), mirroring how
 *    BarterGui/BarterBuyGui work in this project. BarterInventoryListener is expected to cancel
 *    interactions for plugin-owned GUIs.
 *  - If your central listener filters by a custom marker, we provide an InventoryHolder marker below.
 *    (BarterInventoryListener can detect getTopInventory().getHolder() instanceof Holder if needed.)

 * I18n:
 *  - Pulls all strings from messages.yml under buy.log_book_hub.*
 */
public final class LogBookHubGui {

    /** Title shown at the top of the inventory. */
    private static Component title() {
        return Messages.mm("buy.log_book_hub.title");
    }

    /** Exposed slot indices for the listener to route on (no more magic numbers). */
    public static final int SLOT_BACK_MAIN    = 0;
    public static final int SLOT_THIS_STATS   = 2;
    public static final int SLOT_TOP_ITEMS    = 4;
    public static final int SLOT_BUYER_HISTORY= 6;
    public static final int SLOT_ALL_BARRELS  = 8;

    /**
     * Marker holder for this inventory.

     * Purpose:
     *  - Gives the central inventory listener a reliable way to identify this as a plugin GUI,
     *    without relying on titles or item checks.
     *  - Carries the current {@link BarterStore} context so the listener can route actions
     *    (e.g., Barrier click should reopen the main {@code BarterGui} for this store).
     *  - Also retains a reference to the created Inventory so {@link #getInventory()} can satisfy
     *    Bukkit's @NotNull contract.
     */
    public static final class Holder implements InventoryHolder {
        private final BarterStore store;
        private Inventory inventory; // attached in open()

        public Holder(BarterStore store) {
            this.store = Objects.requireNonNull(store, "store");
        }

        public BarterStore getStore() { return store; }

        void attach(Inventory inv) { this.inventory = inv; }

        @Override
        public @NotNull Inventory getInventory() {
            // Should never be null once attached; fallback just in case.
            if (inventory == null) {
                inventory = Bukkit.createInventory(this, 9, title());
            }
            return inventory;
        }
    }

    /**
     * Open the 9-slot hub for the given player.
     * No external behavior is wired here; clicks are handled centrally by BarterInventoryListener.
     *
     * @param player the viewer
     * @param store  the current store context (used by the listener for "Back to Main Menu")
     */
    public static void open(Player player, BarterStore store) {
        // Create holder first so we can attach the inventory reference.
        Holder holder = new Holder(store);
        Inventory inv = Bukkit.createInventory(holder, 9, title());
        holder.attach(inv);

        // Visual separators in slots 1, 3, 5, 7.
        ItemStack filler = pane();
        inv.setItem(1, filler);
        inv.setItem(3, filler);
        inv.setItem(5, filler);
        inv.setItem(7, filler);

        // 0: Barrier → Back to main menu
        inv.setItem(SLOT_BACK_MAIN,
                button(Material.BARRIER,
                        Messages.mm("buy.log_book_hub.back_main_name"),
                        Messages.mmList("buy.log_book_hub.back_main_lore")));

        // 2: Barrel → This Shop's Stats
        inv.setItem(SLOT_THIS_STATS,
                button(Material.BARREL,
                        Messages.mm("buy.log_book_hub.all_stats_name"),
                        Messages.mmList("buy.log_book_hub.all_stats_lore")));

        // 4: Gold Ingot → Top Selling Items — show "Loading…" first, then update async
        inv.setItem(SLOT_TOP_ITEMS,
                button(Material.GOLD_INGOT,
                        Messages.mm("buy.log_book_hub.top_items_name"),
                        Messages.mmList("buy.log_book_hub.top_items_loading_lore")));

        // 6: Compass → Buyer History (wired — listener opens BuyerHistoryGui)
        inv.setItem(SLOT_BUYER_HISTORY,
                button(Material.COMPASS,
                        Messages.mm("buy.log_book_hub.buyer_history_name"),
                        Messages.mmList("buy.log_book_hub.buyer_history_lore")));

        // 8: Beacon → All Barrels Mode (listener opens AllLogBookHubGui)
        inv.setItem(SLOT_ALL_BARRELS,
                button(Material.BEACON,
                        Messages.mm("buy.log_book_hub.all_barrels_name"),
                        Messages.mmList("buy.log_book_hub.all_barrels_lore")));

        // Open now so the user sees the hub immediately
        player.openInventory(inv);

        // Compute top sellers off-thread, then safely update slot 4 on main
        Bukkit.getScheduler().runTaskAsynchronously(BarterContainer.INSTANCE, () -> {
            List<Component> lore = buildTopSellingLoreFromLogs(store);
            Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> {
                if (!player.isOnline()) return;
                Inventory top = player.getOpenInventory().getTopInventory();
                InventoryHolder h = top.getHolder();
                if (h instanceof Holder hh && hh.getStore().equals(store)) {
                    ItemStack updated = button(Material.GOLD_INGOT,
                            Messages.mm("buy.log_book_hub.top_items_name"),
                            lore);
                    top.setItem(SLOT_TOP_ITEMS, updated);
                }
            });
        });
    }

    /* -------------------------------------------------------------------------------------------- *
     *                                         Helpers                                              *
     * -------------------------------------------------------------------------------------------- */

    /**
     * Build a menu button with Adventure display name, optional lore,
     * and hidden vanilla attributes for a clean tooltip.
     */
    private static ItemStack button(Material mat, Component name, List<Component> lore) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        m.displayName(name.colorIfAbsent(NamedTextColor.AQUA));
        if (lore != null && !lore.isEmpty()) m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(m);
        return i;
    }

    /**
     * Reads this store's persisted purchase log and returns up to 3 lore lines
     * using messages.yml keys for header/rows:
     *  - buy.log_book_hub.top_items_ready_lore_header
     *  - buy.log_book_hub.top_items_ready_row (<item>, <amount>)

     * If I/O fails: buy.log_book_hub.top_items_error_lore
     * If empty:     buy.log_book_hub.top_items_empty_lore

     * This runs off-thread (safe, file I/O) and only its result is pushed back
     * onto the main thread to update the GUI item.
     */
    private static List<Component> buildTopSellingLoreFromLogs(BarterStore store) {
        Map<Material, Integer> totals = new EnumMap<>(Material.class);
        try {
            for (TransactionRecord r : BarterShopOwnerLogManager.listAllEntries(store.getKey())) {
                Material mat = r.itemType();
                if (mat == null || mat == Material.AIR) continue;
                totals.merge(mat, Math.max(0, r.amount()), Integer::sum);
            }
        } catch (Exception io) {
            return Messages.mmList("buy.log_book_hub.top_items_error_lore");
        }

        if (totals.isEmpty()) {
            return Messages.mmList("buy.log_book_hub.top_items_empty_lore");
        }

        // Sort by quantity desc and pick 3
        List<Map.Entry<Material,Integer>> top = totals.entrySet().stream()
                .sorted((a,b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(3)
                .toList();

        List<Component> lore = new ArrayList<>(1 + top.size());
        lore.addAll(Messages.mmList("buy.log_book_hub.top_items_ready_lore_header"));
        for (Map.Entry<Material,Integer> e : top) {
            lore.add(Messages.mm("buy.log_book_hub.top_items_ready_row",
                    "item", pretty(e.getKey()),
                    "amount", e.getValue()));
        }
        return lore;
    }

    /** Human-friendly Material name (Title Case). */
    private static String pretty(Material m) {
        String s = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder out = new StringBuilder(s.length());
        boolean cap = true;
        for (char c : s.toCharArray()) {
            if (cap && Character.isLetter(c)) {
                out.append(Character.toUpperCase(c));
                cap = false;
            } else {
                out.append(c);
            }
            if (c == ' ') cap = true;
        }
        return out.toString();
    }

    /**
     * Gray stained glass pane used as a visual separator.
     * Name intentionally set to empty to avoid tooltip noise.
     */
    private static ItemStack pane() {
        ItemStack i = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = i.getItemMeta();
        m.displayName(Component.empty()); // truly blank
        i.setItemMeta(m);
        return i;
    }
}
