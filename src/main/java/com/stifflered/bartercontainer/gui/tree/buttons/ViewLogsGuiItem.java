package com.stifflered.bartercontainer.gui.tree.buttons;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.BarterShopOwnerLogManager;
import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.stifflered.bartercontainer.util.ListPaginator;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ViewLogsGuiItem extends GuiItem {

    private static final Book LOADING = Book.book(Component.text(""), Component.text(""), List.of(Component.text("Loading...")));

    public ViewLogsGuiItem(BarterStore store) {
        super(ItemUtil.wrapEdit(new ItemStack(Material.BOOK), (meta) -> {
            Components.name(meta, Component.text("ⓘ View Logs", TextColor.fromHexString("#cfcfc4")));
            Components.lore(meta, Components.miniSplit("""
                    <gray>Click to <green>view</green> your container's,
                    <gray>purchase logs.
                    """));
        }), (event) -> {
            HumanEntity clicker = event.getWhoClicked();

            clicker.openBook(LOADING);
            new BukkitRunnable(){

                @Override
                public void run() {
                    try {
                        ListPaginator<BarterShopOwnerLogManager.TransactionRecord> recordListPaginator = new ListPaginator<>(BarterShopOwnerLogManager.listAllEntries(store.getKey()).reversed(), 13);
                        List<Component> pages = new ArrayList<>();
                        for (int i = 0; i < Math.min(recordListPaginator.getTotalPages(), 10); i++) { // max of 10
                            TextComponent.Builder builder = Component.text();
                            for (BarterShopOwnerLogManager.TransactionRecord record : recordListPaginator.getPage(i)) {
                                builder.append(record.formatted()).appendNewline();
                            }
                            pages.add(builder.build());
                        }
                        new BukkitRunnable(){

                            @Override
                            public void run() {
                                clicker.openBook(Book.book(Component.text("Data"), Component.text("?"), pages));
                            }
                        }.runTask(BarterContainer.INSTANCE);
                    } catch (IOException e) {
                        clicker.closeInventory();
                        clicker.sendMessage(Component.text("Failed to open logs!", NamedTextColor.RED));
                    }
                }
            }.runTaskAsynchronously(BarterContainer.INSTANCE);
        });
    }


}
