package com.stifflered.bartercontainer.gui.tree.buttons;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class ViewContentsGuiItem extends GuiItem {

    public ViewContentsGuiItem(BarterStore store) {
        super(ItemUtil.wrapEdit(new ItemStack(Material.CHEST), (meta) -> {
            Components.name(meta,  Component.text("⚀ View Shop", TextColor.color(255, 109, 106)));
            Components.lore(meta, Components.miniSplit("""
                    <gray>Click to <green>view</green> your container's items
                    <gray>for sale.
                    """));
        }), (event) -> {
            event.getWhoClicked().openInventory(store.getSaleStorage());
        });
    }


}
