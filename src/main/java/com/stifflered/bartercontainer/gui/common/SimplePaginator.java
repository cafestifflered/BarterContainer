package com.stifflered.bartercontainer.gui.common;

import com.github.stefvanschie.inventoryframework.adventuresupport.TextHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SimplePaginator extends ChestGui {

    private static final ItemStack PAGE_UP = ItemUtil.wrapEdit(new ItemStack(Material.TIPPED_ARROW), (meta) -> {
        Components.name(meta, Component.text("+1 Page", NamedTextColor.GREEN));
        ((PotionMeta) meta).setColor(Color.GREEN);
        meta.addItemFlags(ItemFlag.values());
    });

    private static final ItemStack PAGE_DOWN = ItemUtil.wrapEdit(new ItemStack(Material.TIPPED_ARROW), (meta) -> {
        Components.name(meta, Component.text("-1 Page", NamedTextColor.RED));
        ((PotionMeta) meta).setColor(Color.RED);
        meta.addItemFlags(ItemFlag.values());
    });


    public SimplePaginator(int rows, @NotNull TextHolder textHolder, List<GuiItem> guiItemList, @Nullable Inventory parent) {
        super(rows, textHolder);
        this.setOnTopClick((event) -> {
            event.setCancelled(true);
        });


        PaginatedPane pages = new PaginatedPane(0, 1, 9, 4);
        pages.populateWithGuiItems(guiItemList);

        this.addPane(pages);

        StaticPane navigation = new StaticPane(0, 5, 9, 1);
        if (pages.getPage() > 0) {
            navigation.addItem(new GuiItem(PAGE_DOWN, event -> {
                if (pages.getPage() > 0) {
                    pages.setPage(pages.getPage() - 1);

                    this.update();
                }
            }), 3, 0);
        }

        if ((pages.getPages() - 1) > pages.getPage()) {
            navigation.addItem(new GuiItem(PAGE_UP, event -> {
                if (pages.getPage() < pages.getPages() - 1) {
                    pages.setPage(pages.getPage() + 1);

                    this.update();
                }
            }), 5, 0);

        }
        if (parent != null) {
            navigation.addItem(ItemUtil.back(parent), navigation.getLength() - 1, navigation.getHeight() - 1);
        }
        this.addPane(navigation);
    }

}
