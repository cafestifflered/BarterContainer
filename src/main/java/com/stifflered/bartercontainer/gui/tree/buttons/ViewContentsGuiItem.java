package com.stifflered.bartercontainer.gui.tree.buttons;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.stifflered.bartercontainer.*;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class ViewContentsGuiItem extends GuiItem {

    public ViewContentsGuiItem(BarterStore store) {
        super(BarterContainer.INSTANCE.getConfiguration().getViewShopItem(), (event) -> {
            event.getWhoClicked().openInventory(store.getSaleStorage());
        });
    }


}
