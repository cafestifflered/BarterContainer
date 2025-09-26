package com.stifflered.bartercontainer.gui.common;

import com.github.stefvanschie.inventoryframework.adventuresupport.TextHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.stifflered.bartercontainer.util.Messages;
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
        Components.name(meta, Messages.mm("gui.paginator.next"));
        ((PotionMeta) meta).setColor(Color.GREEN);
        meta.addItemFlags(ItemFlag.values());
    });

    private static final ItemStack PAGE_DOWN = ItemUtil.wrapEdit(new ItemStack(Material.TIPPED_ARROW), (meta) -> {
        Components.name(meta, Messages.mm("gui.paginator.prev"));
        ((PotionMeta) meta).setColor(Color.RED);
        meta.addItemFlags(ItemFlag.values());
    });

    private final StaticPane navigation;
    protected final List<GuiItem> pages;
    private final @Nullable Inventory parent;
    private final PaginatedPane paginatedPane;

    public SimplePaginator(int rows, @NotNull TextHolder textHolder, List<GuiItem> guiItemList, @Nullable Inventory parent) {
        super(rows, textHolder);
        this.setOnTopClick((event) -> {
            event.setCancelled(true);
        });
        this.parent = parent;


        this.paginatedPane = new PaginatedPane(0, 1, 9, 4);
        this.paginatedPane.populateWithGuiItems(guiItemList);

        this.addPane(this.paginatedPane);

        StaticPane header = new StaticPane(0, 0, 9, 1);
        header.fillWith(ItemUtil.BLANK);
        this.addPane(header);

        this.pages = guiItemList;
        this.navigation = new StaticPane(0, 5, 9, 1);
        this.addPane(this.navigation);
        update();
    }

    @Override
    public void update() {
        this.navigation.clear();
        navigation.fillWith(ItemUtil.BLANK);
        PaginatedPane pages = this.paginatedPane;

        if (pages.getPage() > 0) {
            navigation.addItem(new GuiItem(PAGE_DOWN, event -> {
                if (pages.getPage() > 0) {
                    pages.setPage(pages.getPage() - 1);

                    this.update();
                }
            }), 3, 0);
        }

        if (pages.getPage() < pages.getPages() - 1) {
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
        super.update();
    }

}