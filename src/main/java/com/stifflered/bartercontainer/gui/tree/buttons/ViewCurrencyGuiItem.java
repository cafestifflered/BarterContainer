package com.stifflered.bartercontainer.gui.tree.buttons;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class ViewCurrencyGuiItem extends GuiItem {

    public ViewCurrencyGuiItem(BarterStore store) {
        super(ItemUtil.wrapEdit(new ItemStack(Material.CAULDRON), (meta) -> {
            Components.name(meta, Component.text("☐ View Barter Bank", TextColor.color(255, 109, 106)));
            Components.lore(meta, Components.miniSplit("""
                    <gray>Click to <green>view</green> your container's bank,
                    <gray>which contains items that player's
                    <gray>purchased your items with.
                    
                    <blue>ℹ</blue> <white>Players cannot buy from your shop
                    <white>if your bank is full.
                    """));
        }), (event) -> {

            event.getWhoClicked().openInventory(store.getCurrencyStorage());
        });
    }


}
