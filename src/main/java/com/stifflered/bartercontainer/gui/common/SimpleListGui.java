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

public class SimpleListGui extends ChestGui {

    public SimpleListGui(@NotNull List<GuiItem> elements, @NotNull Component title, @Nullable Inventory parent) {
        super(calculateRows(elements, parent), ComponentHolder.of(title));
        this.setOnTopClick(event -> event.setCancelled(true));

        InventoryComponent inventoryComponent = this.getInventoryComponent();
        StaticPane pane = ItemUtil.wrapGui(inventoryComponent, inventoryComponent.getLength(), this.getRows());
        ItemUtil.listItems(pane, elements);
        if (parent != null) {
            pane.addItem(ItemUtil.back(parent), pane.getLength() - 1, 0);
        }
    }

    private static int calculateRows(@NotNull List<GuiItem> elements, @Nullable Inventory parent) {
        int items = elements.size();
        if (parent != null) {
            items += 1; // Add room for back arrow
        }

        return (int) Math.ceil(items / 9f);
    }

}
