package com.stifflered.tcchallenge.gui.tree.buttons;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.AnvilGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.stifflered.tcchallenge.tree.OnlineTree;
import com.stifflered.tcchallenge.util.Components;
import com.stifflered.tcchallenge.util.ItemUtil;
import com.stifflered.tcchallenge.util.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.CompletableFuture;

public class TreeGuiItem extends GuiItem {

    private static final ComponentHolder RENAME_EDITOR = ComponentHolder.of(
            Component.text("✎ Rename Tree")
    );

    private static final ItemStack CONFIRM_RENAME_TREE = ItemUtil.wrapEdit(new ItemStack(Material.EMERALD_BLOCK), (meta) -> {
        Components.name(meta, Component.text("Confirm Rename", NamedTextColor.GREEN, TextDecoration.BOLD));
        Components.lore(meta, Components.miniSplit(
                """
                        <gray>Click to <green>rename</green> this tree.
                        """));
    });

    public TreeGuiItem(OnlineTree tree) {
        super(ItemUtil.wrapEdit(new ItemStack(tree.getType().getPalette().icon()), (meta) -> {
            Components.name(meta, tree.getNameStyled());
            Components.lore(meta, Components.miniSplit("""
                    <gray>Click to <green>edit</green> your tree's name.
                    """));
        }), (event) -> {
            event.getWhoClicked().closeInventory();

            openRenameMenu((Player) event.getWhoClicked(), tree);
        });
    }

    public static CompletableFuture<Void> openRenameMenu(Player player, OnlineTree tree) {
        CompletableFuture<Void> onFinish = new CompletableFuture<>();

        AnvilGui gui = new AnvilGui(RENAME_EDITOR);
        gui.setOnTopClick(e -> e.setCancelled(true));

        // Rename result slot
        {
            ItemStack renameItem = ItemUtil.wrapEdit(new ItemStack(Material.PAPER), (meta) -> {
                meta.displayName(tree.getNameStyled());
            });

            StaticPane pane = ItemUtil.wrapGui(gui.getFirstItemComponent());
            pane.addItem(new GuiItem(renameItem), 0, 0);
        }

        // Rename confirm
        {
            StaticPane pane = ItemUtil.wrapGui(gui.getResultComponent());
            pane.addItem(new GuiItem(CONFIRM_RENAME_TREE, (rename) -> {
                String text = gui.getRenameText();

                if (!text.equals(tree.getRawName())) {
                    if (text.isBlank()) {
                        tree.setRawName(null);
                    } else {
                        tree.setRawName(text);
                    }

                    Sounds.writeText(player);
                }
                player.closeInventory();
                onFinish.complete(null);
            }), 0, 0);
        }

        gui.show(player);

        return onFinish;
    }
}
