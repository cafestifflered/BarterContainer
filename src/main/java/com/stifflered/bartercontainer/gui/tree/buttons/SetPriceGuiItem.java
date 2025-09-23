package com.stifflered.bartercontainer.gui.tree.buttons;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.gui.common.SimpleInnerGui;
import com.stifflered.bartercontainer.gui.tree.BarterGui;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.stifflered.bartercontainer.util.Sounds;
import com.stifflered.bartercontainer.util.Messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * Represents a clickable GUI item for editing the "price" of a barter store.

 * - When added to a GUI, this item allows players with access to open an editor
 *   menu where they can override the current price item.
 * - Uses a {@link SimpleInnerGui} to create a 1-row editor:
 *      Layout (row y=0):
 *        [0] filler  [1] filler  [2] filler  [3] filler  [4] PRICE  [5] filler  [6] filler  [7] filler  [8] BACK
 *   • Only slot 4 (center) accepts an overwrite from the cursor item (without consuming it).
 *   • Slot 8 is a Barrier “Back” that returns to {@link BarterGui}.
 *   • All other top slots are locked filler.
 *   • Number-key hotbar swaps / SWAP_OFFHAND are blocked across the top.

 * Responsibilities:
 *  • Display the "Set Price" button in shop UIs.
 *  • Handle interaction logic to update the price item.
 *  • Provide a utility method {@link #getPriceItem(BarterStore)} for consistent
 *    rendering of the current price item.
 */
public class SetPriceGuiItem extends GuiItem {

    // Single-row layout constants
    private static final int PRICE_SLOT = 4; // center of row
    private static final int BACK_SLOT  = 8; // rightmost

    /**
     * Constructs a clickable "Set Price" item for the given store.
     *
     * @param store The barter store whose price will be edited.
     */
    public SetPriceGuiItem(BarterStore store) {
        super(
                // Fetch the base config item (material/flags/skin), then override name/lore from messages.yml.
                ItemUtil.wrapEdit(
                        BarterContainer.INSTANCE.getConfiguration().getSetPriceItem(
                                Placeholder.component("item", getItemDescription(store.getCurrentItemPrice()))
                        ),
                        meta -> {
                            Components.name(meta, Messages.mm(
                                    "gui.tree.set_price.button_name",
                                    "item", getItemDescription(store.getCurrentItemPrice())
                            ));
                            Components.lore(meta, Messages.mmList(
                                    "gui.tree.set_price.button_lore",
                                    "item", getItemDescription(store.getCurrentItemPrice())
                            ));
                        }
                ),
                (event) -> {
                    // Open edit menu when clicked
                    openEditMenu(store, (Player) event.getWhoClicked());
                }
        );
    }

    /**
     * Opens a small GUI menu that lets the player change the price item
     * by clicking with another item on their cursor.
     *
     * @param store  The barter store being edited.
     * @param player The player performing the edit.
     */
    public static void openEditMenu(BarterStore store, Player player) {
        // Build title like other editors: "<Store Styled>  <localized title>"
        Component title = Messages.mm("gui.tree.set_price.title");

        // Create a 1-row GUI with the composed title
        ChestGui gui = new SimpleInnerGui(1, ComponentHolder.of(title), null);

        // Main pane where we place filler, price, and back
        StaticPane pane = ItemUtil.wrapGui(gui.getInventoryComponent(), 9, 1);

        // ── Filler in all slots except PRICE and BACK
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemUtil.wrapEdit(filler, meta -> Components.name(meta, Component.text(" ")));
        for (int x = 0; x < 9; x++) {
            if (x == PRICE_SLOT || x == BACK_SLOT) continue;
            pane.addItem(new GuiItem(filler), x, 0);
        }

        // ── Price item in center (slot 4)
        ItemStack priceItem = ItemUtil.wrapEdit(getPriceItem(store), meta -> {
            // Instruction lore from messages.yml
            meta.lore(Messages.mmList("gui.tree.set_price.instruction_lore"));
        });
        pane.addItem(new GuiItem(priceItem), PRICE_SLOT, 0);

        // ── Back in slot 8
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemUtil.wrapEdit(back, meta -> {
            Components.name(meta, Messages.mm("util.back_button.name"));
            var lore = Messages.mmList("util.back_button.lore");
            if (!lore.isEmpty()) meta.lore(lore);
        });
        pane.addItem(new GuiItem(back, e -> new BarterGui(store).show(player)), BACK_SLOT, 0);

        // --- Click rules:
        gui.setOnTopClick(e -> {
            int slot = e.getSlot();

            // Block hotbar/offhand swaps entirely in this GUI
            ClickType click = e.getClick();
            if (click == ClickType.NUMBER_KEY || click == ClickType.SWAP_OFFHAND) {
                e.setCancelled(true);
                return;
            }

            // Back button handled by its own GuiItem click; make sure default top action is cancelled here.
            if (slot == BACK_SLOT) {
                e.setCancelled(true);
                return;
            }

            // Only the PRICE slot accepts cursor overwrite (without consuming)
            if (slot == PRICE_SLOT) {
                ItemStack cursor = e.getCursor();
                if (!cursor.getType().isAir()) { // safe null + air check
                    store.setCurrentItemPrice(cursor.clone());
                    Sounds.choose(e.getWhoClicked());
                    openEditMenu(store, player);
                }
                e.setCancelled(true);
                return;
            }

            // All other slots locked
            e.setCancelled(true);
        });

        // Also cancel shift-clicks globally (mass moves)
        gui.setOnGlobalClick(e -> {
            if (e.getClick().isShiftClick()) {
                e.setCancelled(true);
            }
        });

        // Show menu
        gui.show(player);
    }

    /**
     * Generates a simple description for the price item.
     * Example: "Diamond x5"
     *
     * @param currentItemPrice Item currently set as price
     * @return Adventure Component representing the item + amount
     */
    private static Component getItemDescription(ItemStack currentItemPrice) {
        return currentItemPrice.displayName()
                .append(Component.text(" x" + currentItemPrice.getAmount()));
    }

    /**
     * Utility for rendering the price item with correct name and lore
     * for use in GUIs.
     *
     * @param store The barter store
     * @return ItemStack representing the price
     */
    public static ItemStack getPriceItem(BarterStore store) {
        return ItemUtil.wrapEdit(store.getCurrentItemPrice(), (meta) -> {
            Components.name(meta, Messages.mm("gui.tree.set_price.price_item_name"));
            Components.lore(meta, Messages.mm(
                    "gui.tree.set_price.price_item_lore",
                    "item", getItemDescription(store.getCurrentItemPrice())
            ));
        });
    }
}
