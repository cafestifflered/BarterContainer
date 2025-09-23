package com.stifflered.bartercontainer.listeners;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.store.BarterStoreKey;
import com.stifflered.bartercontainer.store.owners.BankOwner;
import com.stifflered.bartercontainer.store.owners.SaveOnClose;
import com.stifflered.bartercontainer.gui.tree.BarterGui;
import com.stifflered.bartercontainer.gui.tree.LogBookHubGui;
import com.stifflered.bartercontainer.gui.tree.ShopStatsGui;
import com.stifflered.bartercontainer.gui.tree.BuyerHistoryGui;
import com.stifflered.bartercontainer.gui.tree.BuyerHistoryAllGui;
import com.stifflered.bartercontainer.gui.tree.BuyerHistoryDetailGui;
import com.stifflered.bartercontainer.gui.tree.AllLogBookHubGui;
import com.stifflered.bartercontainer.gui.tree.AllShopStatsGui;
import com.stifflered.bartercontainer.gui.tree.AllBuyerHistoryGui;
import com.stifflered.bartercontainer.gui.tree.AllBuyerHistoryAllGui;
import com.stifflered.bartercontainer.gui.tree.AllBuyerHistoryDetailGui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

import static org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY;

/**
 * Listener that enforces custom rules on barter store inventories:
 *  - Protects the "bank" inventory (currency storage) from unauthorized changes.
 *  - Ensures barter store inventories are persisted when closed.

 * Core rules:
 *  1) BankOwner inventories:
 *     - Disallow shift-click transfers into/out of the bank.
 *     - Disallow most manual actions (except picking up an item stack).
 *     - Disallow hopper/automation insertion (InventoryMoveItemEvent).
 *     - Disallow drag placement into bank slots.
 *  2) SaveOnClose inventories:
 *     - When a shop’s sale inventory closes, trigger save via BarterManager.

 * GUI routing:
 *  - LogBookHubGui (hub) — inert; slot-based navigation.
 *  - ShopStatsGui      — inert; tile clicks print details; barrier to go back.
 *  - BuyerHistoryGui   — heads grid; supports pagination, back, and "All Purchases".
 *  - BuyerHistoryAllGui — unfiltered purchases list; supports pagination and back.
 *  - BuyerHistoryDetailGui — per-buyer purchases; supports pagination and back.

 * Notes:
 *  - Relies on InventoryHolder tagging for all plugin-owned GUIs and special inventories.
 *  - By design, the bank is effectively read-only (you can only take items out via pickup).
 */
public class BarterInventoryListener implements Listener {

    /**
     * Protects the bank inventory from illegal click actions and routes plugin GUIs.
     * - Cancels shift-clicks into a BankOwner inventory.
     * - Cancels most actions on bank slots (except simple PICKUP_ALL with a non-null item).
     * - Makes plugin GUIs inert and handles slot-based navigation.
     */
    @EventHandler
    public void protect(InventoryClickEvent event) {
        final Inventory top = event.getView().getTopInventory();
        final Object topHolder = top.getHolder();

        // -----------------------
        // LogBookHub (9-slot hub)
        // -----------------------
        if (topHolder instanceof LogBookHubGui.Holder holder) {
            event.setCancelled(true);

            // Only act on clicks in the top inventory (0..size-1)
            final int raw = event.getRawSlot();
            if (raw >= 0 && raw < top.getSize() && event.getWhoClicked() instanceof Player player) {

                if (raw == 0) {
                    // Slot 0 = Barrier → Back to main page (BarterGui) for this store
                    new BarterGui(holder.getStore()).show(player);
                    return;
                }
                if (raw == 2) {
                    // Slot 2 = Barrel → Open Shop Stats
                    ShopStatsGui.open(player, holder.getStore());
                    return;
                }
                if (raw == 6) {
                    // Slot 6 = Compass → Buyer History
                    BuyerHistoryGui.open(player, holder.getStore(), 0);
                    return;
                }
                if (raw == 8) {
                    // Slot 8 = Beacon → All Barrels Mode
                    final UUID ownerId = (holder.getStore().getPlayerProfile() != null)
                            ? holder.getStore().getPlayerProfile().getId()
                            : player.getUniqueId();
                    final String ownerLabel = (holder.getStore().getPlayerProfile() != null)
                            ? holder.getStore().getPlayerProfile().getName()
                            : player.getName();

                    Bukkit.getScheduler().runTaskAsynchronously(BarterContainer.INSTANCE, () -> {
                        Set<BarterStoreKey> keys = new HashSet<>();
                        List<BarterStore> all = BarterManager.INSTANCE.getAll();
                        for (BarterStore s : all) {
                            if (s != null && s.getPlayerProfile() != null
                                    && Objects.equals(ownerId, s.getPlayerProfile().getId())) {
                                keys.add(s.getKey());
                            }
                        }

                        Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> {
                            if (!player.isOnline()) return;
                            AllLogBookHubGui.open(
                                    player,
                                    keys,
                                    ownerLabel,
                                    holder.getStore().getKey()
                            );
                        });
                    });
                    return;
                }
            }
            return;
        }

        // ------------------------------------
        // Shop Stats GUI (inert, explain tiles)
        // ------------------------------------
        if (topHolder instanceof ShopStatsGui.Holder holder) {
            event.setCancelled(true);

            final int raw = event.getRawSlot();
            if (raw >= 0 && raw < top.getSize() && event.getWhoClicked() instanceof Player player) {

                // Route clicks on the consistency tiles to print an explanation in chat.
                // These slot constants come from ShopStatsGui:
                //   SLOT_OVERALL (9)  → Nether Star
                //   SLOT_STABILITY(11)→ Green Dye
                //   SLOT_RECENCY(13)  → Cyan Dye
                //   SLOT_TREND(15)    → Blue Dye
                if (raw == ShopStatsGui.SLOT_OVERALL
                        || raw == ShopStatsGui.SLOT_STABILITY
                        || raw == ShopStatsGui.SLOT_RECENCY
                        || raw == ShopStatsGui.SLOT_TREND) {
                    ShopStatsGui.handleClick(player, raw, holder.getStore());
                    return; // handled
                }

                // Barrier (slot 22) → back to the Logs & Analytics hub
                if (raw == 22) {
                    LogBookHubGui.open(player, holder.getStore());
                    return;
                }
            }
            return;
        }

        // -----------------------------------------------------
        // ALL Shop Stats (27) — Barrier goes back to All-barrels hub
        // -----------------------------------------------------
        // Capture the holder, and use it to reopen the hub with full context
        // Route clicks on the 4 consistency tiles to print aggregate chat breakdowns
        //      (mirrors single-shop behavior but across ALL barrels for this owner).
        if (topHolder instanceof AllShopStatsGui.Holder holder) {
            event.setCancelled(true);

            final int raw = event.getRawSlot();
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (raw < 0 || raw >= top.getSize()) return; // top only

            // Slot 22 is the Barrier in AllShopStatsGui
            if (raw == 22) {
                // Go back to the all-barrels hub using the context stored in the Holder
                AllLogBookHubGui.open(player, holder.getKeys(), holder.getOwnerLabel(), holder.getOriginKey());
                return;
            }

            // Consistency tiles → print detailed aggregate chat messages
            // These slot constants come from AllShopStatsGui:
            //   SLOT_OVERALL (9)  → Nether Star
            //   SLOT_STABILITY(11)→ Green Dye
            //   SLOT_RECENCY(13)  → Cyan Dye
            //   SLOT_TREND(15)    → Blue Dye
            if (raw == AllShopStatsGui.SLOT_OVERALL
                    || raw == AllShopStatsGui.SLOT_STABILITY
                    || raw == AllShopStatsGui.SLOT_RECENCY
                    || raw == AllShopStatsGui.SLOT_TREND) {

                // Prefer the preloaded List<BarterStore> from the Holder (never null, may be empty).
                // Fallback to keys if it's empty.
                List<BarterStore> preloaded = holder.getStores();
                if (!preloaded.isEmpty()) {
                    AllShopStatsGui.handleClick(player, raw, preloaded);   // uses List overload
                } else {
                    AllShopStatsGui.handleClick(player, raw, holder.getKeys()); // fallback to keys overload
                }
                return; // handled
            }

            // Any other click is inert
            return;
        }

        // -----------------------------------------------------
        // Buyer History — top-level heads grid (Large Chest, 54)
        // -----------------------------------------------------
        //
        // Changes:
        //  • Gate Next/Prev using holder.getPages() to block invalid navigation (no blink).
        //  • Keep Top Purchaser (slot 47) clickable.
        //
        if (topHolder instanceof BuyerHistoryGui.Holder holder) {
            event.setCancelled(true);

            final int raw = event.getRawSlot();
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (raw < 0 || raw >= top.getSize()) return; // top only

            // Bottom controls
            if (raw == 49) { // ⛔ Back → Hub
                LogBookHubGui.open(player, holder.getStore());
                return;
            }
            if (raw == 45 && holder.getPage() > 0) { // ← Prev (only if a previous page exists)
                BuyerHistoryGui.open(player, holder.getStore(), holder.getPage() - 1);
                return;
            }
            if (raw == 53) { // → Next (only if not on last page)
                if (holder.getPage() < holder.getPages() - 1) {
                    BuyerHistoryGui.open(player, holder.getStore(), holder.getPage() + 1);
                }
                return; // do nothing if already at last page
            }
            if (raw == 51) { // 📖 All Purchases (unfiltered)
                BuyerHistoryAllGui.open(player, holder.getStore(), 0);
                return;
            }

            // 🥇 Top Purchaser head (slot 47) — open per-buyer detail if present
            if (raw == 47) {
                ItemStack head = top.getItem(47);
                if (head != null && head.getType() == Material.PLAYER_HEAD && head.hasItemMeta()) {
                    UUID buyerId = readBuyerUuidFrom(head);
                    if (buyerId != null) {
                        // Name is best-effort (SkullMeta), safe if null
                        String buyerName = (head.getItemMeta() instanceof SkullMeta sm && sm.getOwningPlayer() != null)
                                ? sm.getOwningPlayer().getName() : null;
                        BuyerHistoryDetailGui.open(player, holder.getStore(), buyerId, buyerName, 0);
                    }
                }
                return;
            }

            // Heads grid (0..44): open per-buyer detail if a head is clicked
            if (raw < 45) {
                ItemStack clicked = top.getItem(raw);
                if (clicked != null && clicked.getType() == Material.PLAYER_HEAD && clicked.hasItemMeta()) {
                    UUID buyerId = readBuyerUuidFrom(clicked);
                    if (buyerId != null) {
                        String buyerName = (clicked.getItemMeta() instanceof SkullMeta sm && sm.getOwningPlayer() != null)
                                ? sm.getOwningPlayer().getName() : null;
                        BuyerHistoryDetailGui.open(player, holder.getStore(), buyerId, buyerName, 0);
                    }
                }
            }
            return;
        }

        // -----------------------------------------------------
        // ALL Buyer History — heads grid (Large Chest, 54)
        // -----------------------------------------------------
        // Change: make Top Purchaser in slot 47 clickable (like the grid heads)
        if (topHolder instanceof AllBuyerHistoryGui.Holder holder) {
            event.setCancelled(true);

            final int raw = event.getRawSlot();
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (raw < 0 || raw >= top.getSize()) return; // top only

            // Bottom controls
            if (raw == 49) { // ⛔ Back → All-barrels hub
                AllLogBookHubGui.open(player, holder.getKeys(), holder.getOwnerLabel(), holder.getOriginKey());
                return;
            }
            if (raw == 45 && holder.getPage() > 0) { // ← Prev
                AllBuyerHistoryGui.open(player, holder.getKeys(), holder.getOwnerLabel(), holder.getOriginKey(), holder.getPage() - 1);
                return;
            }
            if (raw == 53) { // → Next
                if (holder.getPage() < holder.getPages() - 1) {
                    AllBuyerHistoryGui.open(player, holder.getKeys(), holder.getOwnerLabel(), holder.getOriginKey(), holder.getPage() + 1);
                }
                return;
            }
            if (raw == 51) { // 📖 All Purchases
                AllBuyerHistoryAllGui.open(player, holder.getKeys(), holder.getOwnerLabel(), holder.getOriginKey(), 0);
                return;
            }

            // ✅ NEW: Top Purchaser head (slot 47) — open per-buyer detail if present
            if (raw == 47) {
                ItemStack head = top.getItem(47);
                if (head != null && head.getType() == Material.PLAYER_HEAD && head.hasItemMeta()) {
                    UUID buyerId = readBuyerUuidFrom(head); // PDC "buyer_uuid" written by GUI
                    if (buyerId != null) {
                        String buyerName = (head.getItemMeta() instanceof SkullMeta sm && sm.getOwningPlayer() != null)
                                ? sm.getOwningPlayer().getName() : null;
                        AllBuyerHistoryDetailGui.open(player, holder.getKeys(), holder.getOriginKey(), buyerId, buyerName, 0);
                    }
                }
                return;
            }

            // Heads grid (0..44) — open per-buyer detail if a head clicked
            if (raw < 45) {
                ItemStack clicked = top.getItem(raw);
                if (clicked != null && clicked.getType() == Material.PLAYER_HEAD && clicked.hasItemMeta()) {
                    UUID buyerId = readBuyerUuidFrom(clicked);
                    if (buyerId != null) {
                        String buyerName = (clicked.getItemMeta() instanceof SkullMeta sm && sm.getOwningPlayer() != null)
                                ? sm.getOwningPlayer().getName() : null;
                        AllBuyerHistoryDetailGui.open(player, holder.getKeys(), holder.getOriginKey(), buyerId, buyerName, 0);
                    }
                }
            }
            return;
        }

        // ----------------------------------------------------
        // All Single Barrel Purchases — unfiltered list (Large Chest, 54)
        // ----------------------------------------------------
        //
        // Change: gate Next/Prev with holder.getPages().
        //
        if (topHolder instanceof BuyerHistoryAllGui.Holder holder) {
            event.setCancelled(true);

            final int raw = event.getRawSlot();
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (raw < 0 || raw >= top.getSize()) return; // top only

            if (raw == 49) { // ⛔ Back → Buyer list
                BuyerHistoryGui.open(player, holder.getStore(), 0);
                return;
            }
            if (raw == 45 && holder.getPage() > 0) { // ← Prev
                BuyerHistoryAllGui.open(player, holder.getStore(), holder.getPage() - 1);
                return;
            }
            if (raw == 53) { // → Next
                if (holder.getPage() < holder.getPages() - 1) {
                    BuyerHistoryAllGui.open(player, holder.getStore(), holder.getPage() + 1);
                }
                return; // do nothing if already at last page
            }
            return;
        }

        // -----------------------------------------------------
        // ALL Aggregate Purchases — unfiltered list (Large Chest, 54)
        // -----------------------------------------------------
        if (topHolder instanceof AllBuyerHistoryAllGui.Holder holder) {
            event.setCancelled(true);

            final int raw = event.getRawSlot();
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (raw < 0 || raw >= top.getSize()) return; // top only

            if (raw == 49) { // ⛔ Back → Buyer list
                AllBuyerHistoryGui.open(player, holder.getKeys(), holder.getOwnerLabel(), holder.getOriginKey(), 0);
                return;
            }
            if (raw == 45 && holder.getPage() > 0) { // ← Prev
                AllBuyerHistoryAllGui.open(player, holder.getKeys(), holder.getOwnerLabel(), holder.getOriginKey(), holder.getPage() - 1);
                return;
            }
            if (raw == 53) { // → Next
                if (holder.getPage() < holder.getPages() - 1) {
                    AllBuyerHistoryAllGui.open(player, holder.getKeys(), holder.getOwnerLabel(), holder.getOriginKey(), holder.getPage() + 1);
                }
                return;
            }
            return;
        }

        // ---------------------------------------------------
        // Buyer Purchases — per-buyer (Large Chest, 54)
        // ---------------------------------------------------
        //
        // Change: gate Next/Prev with holder.getPages().
        //
        if (topHolder instanceof BuyerHistoryDetailGui.Holder holder) {
            event.setCancelled(true);

            final int raw = event.getRawSlot();
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (raw < 0 || raw >= top.getSize()) return; // top only

            if (raw == 49) { // ⛔ Back → Buyer list
                BuyerHistoryGui.open(player, holder.getStore(), 0);
                return;
            }
            if (raw == 45 && holder.getPage() > 0) { // ← Prev
                BuyerHistoryDetailGui.open(player, holder.getStore(), holder.getBuyer(), holder.getBuyerName(), holder.getPage() - 1);
                return;
            }
            if (raw == 53) { // → Next
                if (holder.getPage() < holder.getPages() - 1) {
                    BuyerHistoryDetailGui.open(player, holder.getStore(), holder.getBuyer(), holder.getBuyerName(), holder.getPage() + 1);
                }
                return; // do nothing if already at last page
            }
            return;
        }

        // -----------------------------------------------------
        // ALL Buyer Purchases — per-buyer (Large Chest, 54)
        // -----------------------------------------------------
        if (topHolder instanceof AllBuyerHistoryDetailGui.Holder holder) {
            event.setCancelled(true);

            final int raw = event.getRawSlot();
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (raw < 0 || raw >= top.getSize()) return; // top only

            if (raw == 49) { // ⛔ Back → All Buyer list
                AllBuyerHistoryGui.open(player, holder.getKeys(), null, holder.getOriginKey(), 0);
                return;
            }
            if (raw == 45 && holder.getPage() > 0) { // ← Prev
                AllBuyerHistoryDetailGui.open(player, holder.getKeys(), holder.getOriginKey(), holder.getBuyer(), holder.getBuyerName(), holder.getPage() - 1);
                return;
            }
            if (raw == 53) { // → Next
                if (holder.getPage() < holder.getPages() - 1) {
                    AllBuyerHistoryDetailGui.open(player, holder.getKeys(), holder.getOriginKey(), holder.getBuyer(), holder.getBuyerName(), holder.getPage() + 1);
                }
                return;
            }
            return;
        }

        // ----------------------------------------------------
        // AllLogBookHub (9-slot, all-barrels hub)
        // ----------------------------------------------------
        if (topHolder instanceof AllLogBookHubGui.Holder holder) {
            event.setCancelled(true);

            final int raw = event.getRawSlot();
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (raw < 0 || raw >= top.getSize()) return; // top only

            // NEW: Barrier (slot 0) -> open this barrel's MAIN MENU (BarterGui)
            if (raw == AllLogBookHubGui.SLOT_BACK_MAIN) {
                // Prefer originKey; otherwise fall back to "any" key from the set so Barrier ALWAYS opens a main menu
                BarterStoreKey preferred = holder.getOriginKey();
                BarterStoreKey targetKey = (preferred != null)
                        ? preferred
                        : holder.getKeys().stream().findFirst().orElse(null);

                if (targetKey == null) { player.closeInventory(); return; }

                Bukkit.getScheduler().runTaskAsynchronously(BarterContainer.INSTANCE, () -> {
                    BarterStore target = null;
                    List<BarterStore> all = BarterManager.INSTANCE.getAll();
                    for (BarterStore s : all) {
                        if (s != null && s.getKey() != null
                                && Objects.equals(s.getKey().key(), targetKey.key())) {
                            target = s;
                            break;
                        }
                    }

                    final BarterStore found = target;
                    Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> {
                        if (!player.isOnline()) return;
                        if (found != null) {
                            new BarterGui(found).show(player);   // MAIN MENU
                        } else {
                            player.closeInventory();
                        }
                    });
                });
                return;
            }

            // Slot 8 (Beacon) -> SWITCH TO SINGLE BARREL MODE (open single-barrel Logs & Analytics hub)
            if (raw == AllLogBookHubGui.SLOT_BACK_THIS) {
                // Prefer originKey; otherwise fall back to any key so this action always works
                BarterStoreKey preferred = holder.getOriginKey();
                BarterStoreKey targetKey = (preferred != null)
                        ? preferred
                        : holder.getKeys().stream().findFirst().orElse(null);

                if (targetKey == null) { player.closeInventory(); return; }

                Bukkit.getScheduler().runTaskAsynchronously(BarterContainer.INSTANCE, () -> {
                    BarterStore target = null;
                    List<BarterStore> all = BarterManager.INSTANCE.getAll();
                    for (BarterStore s : all) {
                        if (s != null && s.getKey() != null
                                && Objects.equals(s.getKey().key(), targetKey.key())) {
                            target = s;
                            break;
                        }
                    }

                    final BarterStore found = target;
                    Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> {
                        if (!player.isOnline()) return;
                        if (found != null) {
                            // Open the SINGLE-BARREL Logs & Analytics hub
                            LogBookHubGui.open(player, found);
                        } else {
                            player.closeInventory();
                        }
                    });
                });
                return;
            }

            if (raw == AllLogBookHubGui.SLOT_ALL_STATS) {
                // Load full BarterStore objects for these keys (needed by AllShopStatsGui)
                Bukkit.getScheduler().runTaskAsynchronously(BarterContainer.INSTANCE, () -> {
                    List<BarterStore> stores = new ArrayList<>();
                    List<BarterStore> all = BarterManager.INSTANCE.getAll();
                    Set<BarterStoreKey> wanted = holder.getKeys();

                    for (BarterStore s : all) {
                        if (s == null || s.getKey() == null) continue;
                        for (BarterStoreKey k : wanted) {
                            if (Objects.equals(s.getKey().key(), k.key())) {
                                stores.add(s);
                                break;
                            }
                        }
                    }

                    Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> {
                        if (!player.isOnline()) return;
                        if (stores.isEmpty()) {
                            player.closeInventory(); // nothing to show
                            return;
                        }
                        // CHANGED: pass keys + owner label + originKey so "Back" can reopen the hub
                        AllShopStatsGui.open(
                                player,
                                stores,
                                holder.getKeys(),
                                holder.getOwnerLabel(),
                                holder.getOriginKey()
                        );
                    });
                });
                return;
            }

            if (raw == AllLogBookHubGui.SLOT_BUYER_HISTORY) {
                // Heads grid across all barrels
                AllBuyerHistoryGui.open(player, holder.getKeys(), holder.getOwnerLabel(), holder.getOriginKey(), 0);
                return;
            }
            return;
        }

        // -----------------------------
        // Bank rules (unchanged below)
        // -----------------------------
        Inventory clickedInventory = event.getClickedInventory();

        // Prevent shift-clicking items into the bank.
        if (event.getAction() == MOVE_TO_OTHER_INVENTORY && topHolder instanceof BankOwner) {
            event.setCancelled(true);
            return;
        }

        // Restrict direct clicks inside the bank inventory.
        if (clickedInventory != null && clickedInventory.getHolder() instanceof BankOwner) {
            // FIX: replace single-case switch with a simple boolean for clarity
            boolean cancel = event.getAction() != InventoryAction.PICKUP_ALL; // allow taking items out

            // If no item in the slot, cancel anyway (prevents oddities).
            if (event.getCurrentItem() == null) {
                cancel = true;
            }

            event.setCancelled(cancel);
        }
    }

    /** Prevent automated transfers (e.g., hoppers) into bank inventories. */
    @EventHandler
    public void protect(InventoryMoveItemEvent event) {
        if (event.getDestination().getHolder() instanceof BankOwner) {
            event.setCancelled(true);
        }
    }

    /** Prevent dragging items while plugin GUIs are open and into bank inventories. */
    @EventHandler
    public void protect(InventoryDragEvent event) {
        final Object topHolder = event.getView().getTopInventory().getHolder();

        // block drags while the log book hub is open
        if (topHolder instanceof LogBookHubGui.Holder) {
            event.setCancelled(true);
            return;
        }

        // block drags while the shop stats GUI is open
        if (topHolder instanceof ShopStatsGui.Holder) {
            event.setCancelled(true);
            return;
        }

        // Block drags while any Buyer History GUI or All-barrels hub is open
        if (topHolder instanceof BuyerHistoryGui.Holder
                || topHolder instanceof BuyerHistoryAllGui.Holder
                || topHolder instanceof BuyerHistoryDetailGui.Holder
                || topHolder instanceof AllLogBookHubGui.Holder
                || topHolder instanceof AllBuyerHistoryGui.Holder
                || topHolder instanceof AllBuyerHistoryAllGui.Holder
                || topHolder instanceof AllBuyerHistoryDetailGui.Holder
                || topHolder instanceof AllShopStatsGui.Holder) {
            event.setCancelled(true);
            return;
        }

        // Bank drag protection (slot-by-slot)
        for (int slot : event.getRawSlots()) {
            // FIX: guard against null from getInventory(slot)
            Inventory inv = event.getView().getInventory(slot);
            if (inv != null && inv.getHolder() instanceof BankOwner) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * When a SaveOnClose inventory (shop’s sale items) is closed,
     * trigger an immediate save of its owning container.
     */
    @EventHandler
    public void close(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SaveOnClose save) {
            BarterManager.INSTANCE.save(save.getContainer());
        }
    }

    private static UUID readBuyerUuidFrom(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        var key = new org.bukkit.NamespacedKey(BarterContainer.INSTANCE, "buyer_uuid");
        String val = pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING);
        if (val != null) {
            try { return java.util.UUID.fromString(val); } catch (IllegalArgumentException ignored) {}
        }
        // Fallback: SkullMeta owning player (legacy)
        if (stack.getItemMeta() instanceof SkullMeta sm && sm.getOwningPlayer() != null) {
            return sm.getOwningPlayer().getUniqueId();
        }
        return null;
    }
}
