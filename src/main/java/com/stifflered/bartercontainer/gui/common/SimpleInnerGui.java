package com.stifflered.bartercontainer.gui.common;

import com.github.stefvanschie.inventoryframework.adventuresupport.TextHolder;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.gui.type.util.Gui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.stifflered.bartercontainer.util.ItemUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A small helper for creating simple, modal-like inner GUIs.

 * Features:
 *  - Creates a ChestGui with a fixed number of rows and a title (TextHolder).
 *  - Cancels all clicks on the top inventory by default (read-only unless you add items with handlers).
 *  - Inserts a full-size StaticPane (length=9, height=rows) for easy layout.
 *  - Optionally adds a standard "Back" button in the bottom-right corner that opens a parent GUI.

 * Usage:
 *  - Extend this class or instantiate directly and then add your own items to the returned pane via getInventoryComponent().
 *  - Provide a parentOpener Supplier<Gui> if this inner GUI should have a back-navigation target.
 */
public class SimpleInnerGui extends ChestGui {

    /**
     * @param rows          number of chest rows (1–6 typical)
     * @param textHolder    GUI title (Adventure/MiniMessage compatible TextHolder)
     * @param parentOpener  optional supplier for a parent GUI; if present, a back button is added
     */
    public SimpleInnerGui(int rows, @NotNull TextHolder textHolder, @Nullable Supplier<Gui> parentOpener) {
        super(rows, textHolder);

        // Block default item movement/interactions on the top inventory
        this.setOnTopClick((event) -> event.setCancelled(true));

        // Create a full-size StaticPane covering the entire GUI grid (9 columns × rows)
        StaticPane pane = ItemUtil.wrapGui(this.getInventoryComponent(), 9, rows);

        // If a parent GUI is provided, add a "Back" button at bottom-right corner
        if (parentOpener != null) {
            pane.addItem(ItemUtil.back(parentOpener), pane.getLength() - 1, pane.getHeight() - 1);
        }

        // Register the pane with this GUI
        this.addPane(pane);
    }

}
