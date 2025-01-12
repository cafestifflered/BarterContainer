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

public class ViewCurrencyGuiItem extends GuiItem {

    public ViewCurrencyGuiItem(BarterStore store) {
        super(BarterContainer.INSTANCE.getConfiguration().getViewBarterBankItem(), (event) -> {
            event.getWhoClicked().openInventory(store.getCurrencyStorage());
        });
    }


}
