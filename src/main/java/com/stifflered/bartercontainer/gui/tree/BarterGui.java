package com.stifflered.bartercontainer.gui.tree;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.gui.tree.buttons.SetPriceGuiItem;
import com.stifflered.bartercontainer.gui.tree.buttons.ViewContentsGuiItem;
import com.stifflered.bartercontainer.gui.tree.buttons.ViewCurrencyGuiItem;
import com.stifflered.bartercontainer.gui.tree.buttons.ViewLogsGuiItem;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.ItemUtil;

public class BarterGui extends ChestGui {

    public BarterGui(BarterStore barterStore) {
        super(3, ComponentHolder.of(barterStore.getNameStyled()));
        this.setOnGlobalClick((event) -> {
            event.setCancelled(true);
        });

        StaticPane pane = ItemUtil.wrapGui(this.getInventoryComponent());

        pane.addItem(new ViewContentsGuiItem(barterStore), 2, 1);

        pane.addItem(new SetPriceGuiItem(barterStore), 4, 1);

        pane.addItem(new ViewCurrencyGuiItem(barterStore), 6, 1);
        pane.addItem(new ViewLogsGuiItem(barterStore), 8, 0);
        this.setOnClose((event) -> {
            BarterManager.INSTANCE.save(barterStore);
        });
    }
    
}
