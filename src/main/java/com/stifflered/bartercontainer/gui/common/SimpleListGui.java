package com.stifflered.bartercontainer.gui.common;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.InventoryComponent;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.stifflered.bartercontainer.util.ItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A simple GUI to display a list of {@link GuiItem}s in a chest-like grid.

 * Features:
 *  - Dynamically sizes itself (number of rows) based on the number of elements.
 *  - Cancels all clicks on the top inventory (read-only unless items have handlers).
 *  - Lists items in order across rows, filling left-to-right, top-to-bottom.
 *  - Optionally adds a "Back" button (linked to a parent {@link Inventory}) in the top-right corner.

 * Intended use:
 *  - Quick lists of items with click behavior (e.g., menus, catalogues, options).
 *  - Use {@link ItemUtil#listItems(StaticPane, List)} to place elements into the pane grid.
 */
public class SimpleListGui extends ChestGui {

    /**
     * Construct a new simple list GUI.
     *
     * @param elements list of items to display
     * @param title    GUI title component
     * @param parent   optional parent inventory; if present, adds a back button in the top-right corner
     */
    public SimpleListGui(@NotNull List<GuiItem> elements, @NotNull Component title, @Nullable Inventory parent) {
        // Calculate number of rows needed to fit elements (and back button if present)
        super(calculateRows(elements, parent), ComponentHolder.of(title));

        // Block default item movement/interactions on the top inventory
        this.setOnTopClick(event -> event.setCancelled(true));

        // Get the root component and wrap it in a StaticPane
        InventoryComponent inventoryComponent = this.getInventoryComponent();
        StaticPane pane = ItemUtil.wrapGui(inventoryComponent, inventoryComponent.getLength(), this.getRows());

        // Fill the pane with provided elements in order
        ItemUtil.listItems(pane, elements);

        // If a parent is provided, add a back button at top-right slot
        if (parent != null) {
            pane.addItem(ItemUtil.back(parent), pane.getLength() - 1, 0);
        }
    }

    /**
     * Calculates number of chest rows required to fit all elements.
     * Each row holds 9 slots. Adds +1 slot if a parent back button is included.
     *
     * @param elements list of items to display
     * @param parent   optional parent (adds one more slot)
     * @return number of rows required (ceil of items/9)
     */
    private static int calculateRows(@NotNull List<GuiItem> elements, @Nullable Inventory parent) {
        int items = elements.size();
        if (parent != null) {
            items += 1; // Account for the back button
        }

        return (int) Math.ceil(items / 9f);
    }

}
