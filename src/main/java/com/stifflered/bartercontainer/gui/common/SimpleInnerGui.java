package com.stifflered.bartercontainer.gui.common;

import com.github.stefvanschie.inventoryframework.adventuresupport.TextHolder;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.gui.type.util.Gui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.stifflered.bartercontainer.util.ItemUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class SimpleInnerGui extends ChestGui {


    public SimpleInnerGui(int rows, @NotNull TextHolder textHolder, @Nullable Supplier<Gui> parentOpener) {
        super(rows, textHolder);
        this.setOnTopClick((event) -> {
            event.setCancelled(true);
        });

        StaticPane pane = ItemUtil.wrapGui(this.getInventoryComponent(), 9, rows);
        if (parentOpener != null) {
            pane.addItem(ItemUtil.back(parentOpener), pane.getLength() - 1, pane.getHeight() - 1);
        }
        this.addPane(pane);
    }

}
