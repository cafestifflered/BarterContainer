package com.stifflered.bartercontainer.gui.tree;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.store.BarterStoreKey;
import com.stifflered.bartercontainer.util.BarterShopOwnerLogManager;
import com.stifflered.bartercontainer.util.BarterShopOwnerLogManager.TransactionRecord;
import com.stifflered.bartercontainer.util.Messages;

import net.kyori.adventure.text.Component;

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
 * 9-slot "All Barrels — Logs & Analytics" hub.

 * Layout (mirrors single-barrel hub):
 *  Index: 0   1   2   3   4   5   6     7   8
 *         ⛔  ░  📦  ░  🥇  ░  🧭  ░  🔆
 * Items:
 *   0 = Barrier     → Back to *This Barrel* Main Menu
 *   2 = Barrel      → All Shop Stats (AllShopStatsGui)
 *   4 = Gold Ingot  → Top Selling Items (across all barrels) [async lore]
 *   6 = Compass     → All Buyer History (AllBuyerHistoryGui)
 *   8 = Beacon      → Single Barrel Mode (open single-barrel Logs & Analytics)
 *   1,3,5,7 = Gray panes
 */
public final class AllLogBookHubGui {

    /* ------------------------------------------------------------------------
     * Title now comes from messages.yml:
     *   all_log_book_hub.title_ownerless / title_with_owner
     * --------------------------------------------------------------------- */
    private static Component title(String ownerLabel) {
        return (ownerLabel == null || ownerLabel.isBlank())
                ? Messages.mm("gui.all_log_book_hub.title_ownerless")
                : Messages.mm("gui.all_log_book_hub.title_with_owner", "owner", ownerLabel);
    }

    // Exposed slots for listener routing
    public static final int SLOT_BACK_MAIN    = 0;
    public static final int SLOT_ALL_STATS    = 2;
    public static final int SLOT_TOP_ITEMS    = 4;
    public static final int SLOT_BUYER_HISTORY= 6;
    public static final int SLOT_BACK_THIS    = 8;

    public static final class Holder implements InventoryHolder {
        private final Set<BarterStoreKey> keys;  // all barrels for this owner
        private final String ownerLabel;
        private final BarterStoreKey originKey;  // the shop we came from (for Back)
        private Inventory inv;

        public Holder(Collection<BarterStoreKey> keys, String ownerLabel, BarterStoreKey originKey) {
            this.keys = Set.copyOf(Objects.requireNonNull(keys, "keys"));
            this.ownerLabel = ownerLabel;
            this.originKey = originKey; // may be null if opened from elsewhere
        }
        public Set<BarterStoreKey> getKeys() { return keys; }
        public String getOwnerLabel() { return ownerLabel; }
        public BarterStoreKey getOriginKey() { return originKey; }
        void attach(Inventory inv) { this.inv = inv; }
        @Override public @NotNull Inventory getInventory() {
            if (inv == null) inv = Bukkit.createInventory(this, 9, title(ownerLabel));
            return inv;
        }
    }

    public static void open(Player player,
                            Collection<BarterStoreKey> keys,
                            String ownerLabel,
                            BarterStoreKey originKey) {
        Holder holder = new Holder(keys, ownerLabel, originKey);
        Inventory inv = Bukkit.createInventory(holder, 9, title(ownerLabel));
        holder.attach(inv);

        // separators
        ItemStack filler = pane();
        inv.setItem(1, filler); inv.setItem(3, filler); inv.setItem(5, filler); inv.setItem(7, filler);

        // 0: Back to This Barrel Hub (or generic back if origin unknown)
        inv.setItem(AllLogBookHubGui.SLOT_BACK_MAIN,
                button(Material.BARRIER,
                        Messages.mm("gui.all_log_book_hub.back_main_name"),
                        Messages.mmList("gui.all_log_book_hub.back_main_lore")));

        // 2: All Shop Stats
        inv.setItem(SLOT_ALL_STATS, button(Material.BARREL,
                Messages.mm("gui.all_log_book_hub.all_stats_name"),
                Messages.mmList("gui.all_log_book_hub.all_stats_lore")));

        // 4: Top Selling Items (all barrels) — show Loading… then update async
        inv.setItem(SLOT_TOP_ITEMS, button(Material.GOLD_INGOT,
                Messages.mm("gui.all_log_book_hub.top_items_name"),
                Messages.mmList("gui.all_log_book_hub.top_items_loading_lore")));

        // 6: All Buyer History
        inv.setItem(SLOT_BUYER_HISTORY, button(Material.COMPASS,
                Messages.mm("gui.all_log_book_hub.buyer_history_name"),
                Messages.mmList("gui.all_log_book_hub.buyer_history_lore")));

        // 8: Switch to single-barrel Logs & Analytics (LogBookHubGui)
        inv.setItem(AllLogBookHubGui.SLOT_BACK_THIS,
                button(Material.BEACON,
                        Messages.mm("gui.all_log_book_hub.single_barrel_mode_name"),
                        Messages.mmList("gui.all_log_book_hub.single_barrel_mode_lore")));

        player.openInventory(inv);

        // async compute top sellers across all keys
        Bukkit.getScheduler().runTaskAsynchronously(BarterContainer.INSTANCE, () -> {
            List<Component> lore = buildTopSellingLoreAcrossKeys(keys);
            Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> {
                if (!player.isOnline()) return;
                Inventory top = player.getOpenInventory().getTopInventory();
                if (!(top.getHolder() instanceof Holder h)) return;
                if (!h.getKeys().equals(Set.copyOf(keys))) return;
                top.setItem(SLOT_TOP_ITEMS, button(Material.GOLD_INGOT,
                        Messages.mm("gui.all_log_book_hub.top_items_name"),
                        lore));
            });
        });
    }

    /* ----------------------------- helpers ----------------------------- */

    /**
     * Builds the lore block for the "Top Selling Items" tile using messages.yml keys:
     *   - top_items_ready_lore_header
     *   - top_items_ready_row (<item>, <amount>)
     *   - top_items_error_lore
     *   - top_items_empty_lore
     */
    private static List<Component> buildTopSellingLoreAcrossKeys(Collection<BarterStoreKey> keys) {
        Map<Material, Integer> totals = new EnumMap<>(Material.class);
        try {
            for (BarterStoreKey k : keys) {
                for (TransactionRecord r : BarterShopOwnerLogManager.listAllEntries(k)) {
                    Material m = r.itemType();
                    if (m == null || m == Material.AIR) continue;
                    totals.merge(m, Math.max(0, r.amount()), Integer::sum);
                }
            }
        } catch (Exception e) {
            return Messages.mmList("gui.all_log_book_hub.top_items_error_lore");
        }

        if (totals.isEmpty()) {
            return Messages.mmList("gui.all_log_book_hub.top_items_empty_lore");
        }

        List<Map.Entry<Material,Integer>> top = totals.entrySet().stream()
                .sorted((a,b)->Integer.compare(b.getValue(), a.getValue()))
                .limit(3)
                .toList();

        List<Component> lore = new ArrayList<>(1 + top.size());
        lore.add(Messages.mm("gui.all_log_book_hub.top_items_ready_lore_header"));
        for (var e : top) {
            lore.add(Messages.mm("gui.all_log_book_hub.top_items_ready_row",
                    "item", pretty(e.getKey()),
                    "amount", e.getValue()));
        }
        return lore;
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
        ItemMeta m = i.getItemMeta(); m.displayName(Component.empty()); i.setItemMeta(m); return i;
    }

    private static String pretty(Material m) {
        String s = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder out = new StringBuilder(s.length());
        boolean cap = true;
        for (char c : s.toCharArray()) {
            if (cap && Character.isLetter(c)) { out.append(Character.toUpperCase(c)); cap = false; }
            else out.append(c);
            if (c == ' ') cap = true;
        }
        return out.toString();
    }
}
