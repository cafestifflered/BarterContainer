package com.stifflered.bartercontainer.gui.tree.buttons;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.gui.type.util.Gui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.stifflered.bartercontainer.*;
import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.gui.common.SimpleInnerGui;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.stifflered.bartercontainer.util.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Supplier;

public class SetPriceGuiItem extends GuiItem {

    private static final ComponentHolder HOLDER = ComponentHolder.of(
            Component.text("Set Price")
    );

    public SetPriceGuiItem(BarterStore store) {
        super(BarterContainer.INSTANCE.getConfiguration().getSetPriceItem(Placeholder.component("item", getItemDescription(store.getCurrentItemPrice()))), (event) -> {
            openEditMenu(store, (Player) event.getWhoClicked());
        });
    }

    public static void openEditMenu(BarterStore store, Player player) {
        ChestGui gui = new SimpleInnerGui(1, HOLDER, null);
        gui.setOnTopClick((event) -> {
            event.setCancelled(true);
        });
        gui.setOnGlobalClick((event) -> {
            if (event.getClick().isShiftClick()) {
                event.setCancelled(true);
            }
        });

        StaticPane pane = ItemUtil.wrapGui(gui.getInventoryComponent());
        pane.addItem(new GuiItem(ItemUtil.wrapEdit(getPriceItem(store), (meta) -> {
                    Components.lore(meta, Components.miniSplit("""
                    <gray>Click with an item on your cursor
                    <gray>to <green>override</green> the price item.""")
                    );
                }), (editEvent) -> {
            ItemStack itemStack = editEvent.getCursor();
            if (itemStack.getType().isAir()) {
                return;
            }

            store.setCurrentItemPrice(itemStack);

            Sounds.choose(editEvent.getWhoClicked());
            openEditMenu(store, player);
        }), 4, 0);

        gui.show(player);
    }

    private static Component getItemDescription(ItemStack currentItemPrice) {
        return currentItemPrice.displayName().append(Component.text(" x" + currentItemPrice.getAmount()));
    }

    public static ItemStack getPriceItem(BarterStore store) {
        return ItemUtil.wrapEdit(store.getCurrentItemPrice(), (meta) -> {
            Components.name(meta, BarterContainer.INSTANCE.getConfiguration().getPriceGuiItemName());
            Components.lore(meta, getItemDescription(store.getCurrentItemPrice()));
        });
    }

}
