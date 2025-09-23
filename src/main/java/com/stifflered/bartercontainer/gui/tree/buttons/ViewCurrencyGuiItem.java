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

import java.util.EnumSet;

/**
 * GUI item that lets a player view the "Barter Bank" (currency storage) of a store.

 * - This acts as a button inside barter GUIs.
 * - The item appearance (material, name, lore) is defined in config.yml under
 *   "view-barter-bank-item".
 * - When clicked, it opens a 4-row wrapper that mirrors {@link BarterStore#getCurrencyStorage()}
 *   with a Back row.

 * 💡 Behavior:
 *   • Rows 0..2 are **withdraw-only**:
 *       - Allowed: take items out (pickup, shift-click to player).
 *       - Disallowed: any placement/swap/collect/hotbar moves into the bank.
 *   • Row 3 is locked filler with center Barrier “Back” to {@link BarterGui}.
 *   • After allowed withdrawals, rows 0..2 are synced back to the real currency storage.
 *   • Hotbar swaps (NUMBER_KEY) and SWAP_OFFHAND are blocked across the top inventory.
 */
public class ViewCurrencyGuiItem extends GuiItem {

    public ViewCurrencyGuiItem(BarterStore store) {
        super(
                // Display item defined in configuration; name/lore overridden by messages.yml
                ItemUtil.wrapEdit(
                        BarterContainer.INSTANCE.getConfiguration().getViewBarterBankItem(),
                        meta -> {
                            Components.name(meta, Messages.mm("gui.tree.view_currency.name"));
                            Components.lore(meta, Messages.mmList("gui.tree.view_currency.lore"));
                        }
                ),
                (event) -> {
                    if (event.getWhoClicked() instanceof Player player) {
                        openWithdrawOnlyWithBack(
                                player,
                                store,
                                store.getCurrencyStorage(),
                                Messages.mm("gui.tree.view_currency.title")
                        );
                    }
                }
        );
    }

    // Actions that represent withdrawing from the top (bank) inventory
    private static final EnumSet<InventoryAction> ALLOWED_WITHDRAW_ACTIONS = EnumSet.of(
            InventoryAction.PICKUP_ALL,
            InventoryAction.PICKUP_HALF,
            InventoryAction.PICKUP_SOME,
            InventoryAction.PICKUP_ONE,
            InventoryAction.MOVE_TO_OTHER_INVENTORY, // shift-click from top → player inv
            InventoryAction.DROP_ALL_CURSOR,         // dropping from cursor (not adding to bank)
            InventoryAction.DROP_ONE_CURSOR
    );

    private static void openWithdrawOnlyWithBack(Player player, BarterStore store, Inventory source, Component titleSuffix) {
        ChestGui gui = new ChestGui(4, ComponentHolder.of(titleSuffix));
        StaticPane pane = ItemUtil.wrapGui(gui.getInventoryComponent(), 9, 4);

        // Locked row 3 filler + back
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemUtil.wrapEdit(filler, meta -> Components.name(meta, Component.text(" ")));
        for (int x = 0; x < 9; x++) {
            if (x == 4) continue;
            pane.addItem(new GuiItem(filler), x, 3);
        }
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemUtil.wrapEdit(back, meta -> {
            Components.name(meta, Messages.mm("util.back_button.name"));
            var lore = Messages.mmList("util.back_button.lore");
            if (!lore.isEmpty()) meta.lore(lore);
        });
        pane.addItem(new GuiItem(back, e -> new BarterGui(store).show(player)), 4, 3);

        // Helper: schedule a next-tick flush + save
        Runnable saveStore = () -> BarterContainer.INSTANCE.getServer().getScheduler().runTask(
                BarterContainer.INSTANCE,
                () -> com.stifflered.bartercontainer.barter.BarterManager.INSTANCE.save(store)
        );
        java.util.function.BiConsumer<InventoryView, Boolean> scheduleFlush = (view, alsoSave) -> BarterContainer.INSTANCE.getServer().getScheduler().runTask(
                BarterContainer.INSTANCE,
                () -> {
                    flushTopToSource(view.getTopInventory(), source);
                    if (alsoSave) saveStore.run();
                }
        );

        // Enforce withdraw-only rules for clicks that originate on the TOP inventory
        gui.setOnTopClick(e -> {
            int slot = e.getSlot();

            // Lock entire 4th row
            if (slot >= 27) {
                e.setCancelled(true);
                return;
            }

            // Block number-key hotbar swaps and offhand swap into top
            ClickType click = e.getClick();
            if (click == ClickType.NUMBER_KEY || click == ClickType.SWAP_OFFHAND) {
                e.setCancelled(true);
                return;
            }

            // Only allow withdrawal-type actions from the bank
            InventoryAction action = e.getAction();
            if (!ALLOWED_WITHDRAW_ACTIONS.contains(action)) {
                e.setCancelled(true);
                return;
            }

            // Allowed withdrawal → after Bukkit applies it, sync GUI → source (slots 0..26)
            scheduleFlush.accept(e.getView(), true);
        });

        // Global protections:
        //  • Prevent touching the locked 4th row.
        //  • Prevent shift-click from BOTTOM inventory moving into TOP (deposit into bank).
        //  • If a bottom "collect to cursor" withdraws from top, allow but flush.
        gui.setOnGlobalClick(e -> {
            // Block any direct interaction with row 3 in top inventory
            if (e.getView().getTopInventory() == e.getClickedInventory()) {
                int raw = e.getRawSlot();
                if (raw >= 27) {
                    e.setCancelled(true);
                }
            } else if (e.getClickedInventory() == e.getView().getBottomInventory()) {
                // From the player's inventory:
                if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                    // shift-click deposit attempt → cancel
                    e.setCancelled(true);
                    return;
                }
                if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                    // Double-click collect may pull matching items from TOP into the cursor.
                    // Allow as a withdrawal, but flush after it happens.
                    scheduleFlush.accept(e.getView(), true);
                }
            }
        });

        // Disallow any drag that targets TOP rows (prevents deposits via drag),
        // and cancel drags that touch the locked row.
        gui.setOnGlobalDrag((InventoryDragEvent e) -> {
            boolean touchesTopEditable = false;
            for (int raw : e.getRawSlots()) {
                if (raw >= 27 && raw < 36) { // locked row
                    e.setCancelled(true);
                    return;
                }
                if (raw >= 0 && raw < 27) {
                    touchesTopEditable = true;
                }
            }
            if (touchesTopEditable) {
                // Any drag over editable top slots would place items → disallow (withdraw-only)
                e.setCancelled(true);
            }
        });

        // SAFETY NET: When the GUI closes for ANY reason, do a final flush so nothing gets voided.
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
