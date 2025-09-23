package com.stifflered.bartercontainer.gui.tree.buttons;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.gui.tree.BarterGui;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.stifflered.bartercontainer.util.Messages;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * GUI item that allows a player to directly view the shop's sale storage.
 * - Appears in barter-related GUIs as a button (item defined in config.yml).
 * - When clicked, it opens the underlying {@link BarterStore#getSaleStorage()}
 *   inventory.

 * ⚠ NEW:
 *   • Instead of opening the raw inventory, we open a 4-row wrapper GUI:
 *       - Rows 0..2 mirror the sale storage and are FULLY EDITABLE.
 *       - Row 3 is locked filler with a center Barrier “Back” to {@link BarterGui}.
 *   • After any allowed edit, rows 0..2 are synced back to the real sale storage.
 *   • Hotbar swaps (NUMBER_KEY) and SWAP_OFFHAND are blocked to prevent bypassing the lock row.
 */
public class ViewContentsGuiItem extends GuiItem {

    /**
     * Constructs the "View Shop Contents" GUI button.
     *
     * @param store The barter store whose sale storage inventory is shown.
     */
    public ViewContentsGuiItem(BarterStore store) {
        super(
                // Item visual (material/skin) pulled from config.yml,
                // then override name/lore from messages.yml for single source of truth.
                ItemUtil.wrapEdit(
                        BarterContainer.INSTANCE.getConfiguration().getViewShopItem(),
                        meta -> {
                            Components.name(meta, Messages.mm("gui.tree.view_contents.name"));
                            Components.lore(meta, Messages.mmList("gui.tree.view_contents.lore"));
                        }
                ),
                (event) -> {
                    if (event.getWhoClicked() instanceof Player player) {
                        openShopEditorWithBack(
                                player,
                                store,
                                store.getSaleStorage(),
                                Messages.mm("gui.tree.view_contents.title")
                        );
                    }
                }
        );
    }

    /**
     * Opens a 4-row GUI:
     *  • Rows 0..2: live editor for the sale storage (27 slots, add/remove/move allowed)
     *  • Row 3: filler panes, with center Barrier “Back” → returns to BarterGui

     * All edits are mirrored back to the real {@code source} inventory after each click.
     * Prevents hotbar-number and offhand swaps from affecting any top slots.
     */
    private static void openShopEditorWithBack(Player player, BarterStore store, Inventory source, Component titleSuffix) {
        // 4 rows; title = styled store name (+ tiny suffix to hint view)
        ChestGui gui = new ChestGui(4, ComponentHolder.of(titleSuffix));

        // Full canvas
        StaticPane pane = ItemUtil.wrapGui(gui.getInventoryComponent(), 9, 4);

        // Locked row 3: filler except center back
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemUtil.wrapEdit(filler, meta -> Components.name(meta, Component.text(" ")));
        for (int x = 0; x < 9; x++) {
            if (x == 4) continue; // (4,3) is back
            pane.addItem(new GuiItem(filler), x, 3);
        }

        // Back button in center of last row
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemUtil.wrapEdit(back, meta -> {
            Components.name(meta, Messages.mm("util.back_button.name"));
            var lore = Messages.mmList("util.back_button.lore");
            if (!lore.isEmpty()) meta.lore(lore);
        });
        pane.addItem(new GuiItem(back, e -> new BarterGui(store).show(player)), 4, 3);

        // Helper: flush top rows (0..26) → source, then save
        Runnable saveStore = () -> BarterContainer.INSTANCE.getServer().getScheduler().runTask(
                BarterContainer.INSTANCE,
                () -> com.stifflered.bartercontainer.barter.BarterManager.INSTANCE.save(store)
        );

        // Single top-click handler:
        //  • Bottom row (27..35) → cancel (locked)
        //  • NUMBER_KEY / SWAP_OFFHAND anywhere in top → cancel
        //  • Top rows (0..26) → allow edit, then sync GUI → source next tick
        gui.setOnTopClick(e -> {
            int slot = e.getSlot();

            // Lock entire 4th row
            if (slot >= 27) {
                e.setCancelled(true);
                return;
            }

            // Block number-key hotbar swaps and offhand swaps into GUI
            ClickType click = e.getClick();
            if (click == ClickType.NUMBER_KEY || click == ClickType.SWAP_OFFHAND) {
                e.setCancelled(true);
                return;
            }

            // Allow edit, then mirror back to the real sale storage (next tick so Bukkit applies the click)
            scheduleFlush(e.getView(), source, /*alsoSave*/  saveStore);
        });

        // Disallow interacting with the locked row via global drag/shift targeting,
        // and ensure drags affecting top rows are flushed.
        gui.setOnGlobalDrag((InventoryDragEvent e) -> {
            // Cancel if any raw slot in locked row (>=27 within the top inventory)
            for (int raw : e.getRawSlots()) {
                if (raw >= 27 && raw < 36) {
                    e.setCancelled(true);
                    return;
                }
            }
            // If any affected slot is in editable top rows, flush next tick
            boolean touchesTopEditable = e.getRawSlots().stream().anyMatch(raw -> raw >= 0 && raw < 27);
            if (touchesTopEditable) {
                scheduleFlush(e.getView(), source, /*alsoSave*/  saveStore);
            }
        });

        // Global click guard:
        // - If clicking in top locked row → cancel
        // - If clicking in bottom inventory:
        //     • shift-click may push into top → flush next tick
        //     • double-click collect-to-cursor may pull from top → flush next tick
        gui.setOnGlobalClick(e -> {
            if (e.getView().getTopInventory() == e.getClickedInventory()) {
                int raw = e.getRawSlot();
                if (raw >= 27) {
                    e.setCancelled(true);
                }
            } else {
                // Clicked in bottom inventory
                if (e.isShiftClick()) {
                    // Shift-move might place items into top; flush after Bukkit processes it
                    scheduleFlush(e.getView(), source, /*alsoSave*/ saveStore);
                } else if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                    // Double-click collect may pull matching items from TOP into the cursor.
                    scheduleFlush(e.getView(), source, /*alsoSave*/ saveStore);
                }
            }
        });

        // SAFETY NET: When the GUI closes for ANY reason, do a final synchronous flush so nothing gets voided.
        gui.setOnClose(e -> {
            Inventory top = e.getInventory();
            flushTopToSource(top, source);
            saveStore.run();
        });

        // Show the GUI, then mirror initial contents into rows 0..2
        gui.show(player);
        Inventory top = gui.getInventory();
        for (int i = 0; i < 27 && i < source.getSize() && i < top.getSize(); i++) {
            ItemStack s = source.getItem(i);
            top.setItem(i, (s == null ? null : s.clone()));
        }
    }

    /** Schedules a next-tick copy of rows 0..26 from the view's top inventory into the source inventory. */
    private static void scheduleFlush(InventoryView view, Inventory source, Runnable saveStore) {
        BarterContainer.INSTANCE.getServer().getScheduler().runTask(
                BarterContainer.INSTANCE,
                () -> {
                    flushTopToSource(view.getTopInventory(), source);
                    if (saveStore != null) saveStore.run();
                }
        );
    }

    /** Copies rows 0..26 (or less if inventories are smaller) from top → source. */
    private static void flushTopToSource(Inventory top, Inventory source) {
        if (top == null || source == null) return;
        int limit = Math.min(27, Math.min(top.getSize(), source.getSize()));
        for (int i = 0; i < limit; i++) {
            ItemStack s = top.getItem(i);
            source.setItem(i, (s == null ? null : s.clone()));
        }
    }
}
