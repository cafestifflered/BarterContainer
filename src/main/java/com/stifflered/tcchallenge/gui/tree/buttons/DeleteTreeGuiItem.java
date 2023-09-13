package com.stifflered.tcchallenge.gui.tree.buttons;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.stifflered.tcchallenge.events.PlayerLoseEvent;
import com.stifflered.tcchallenge.tree.OnlineTree;
import com.stifflered.tcchallenge.tree.TreeManager;
import com.stifflered.tcchallenge.util.Components;
import com.stifflered.tcchallenge.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class DeleteTreeGuiItem extends GuiItem {

    private static final ItemStack DELETE_TREE = ItemUtil.wrapEdit(new ItemStack(Material.BARRIER), (meta) -> {
        Components.name(meta, Component.text("\uD83D\uDD25 Delete your Tree", NamedTextColor.RED, TextDecoration.UNDERLINED));
        Components.lore(meta, Components.miniSplit(
                """
                        <gray>Delete your tree, this is <red>permanent</red>.
                        """));
    });

    public DeleteTreeGuiItem(OnlineTree tree) {
        super(DELETE_TREE, (event) -> {
            ConfirmDeleteTree confirmDeleteTree = new ConfirmDeleteTree(tree);
            confirmDeleteTree.show(event.getWhoClicked());
        });
    }

    private static class ConfirmDeleteTree extends ChestGui {

        private static final ComponentHolder HOLDER = ComponentHolder.of(
                Component.text("Delete Tree")
        );

        private static final ItemStack CONFIRM_DELETION = ItemUtil.wrapEdit(new ItemStack(Material.EMERALD_BLOCK), (meta) -> {
            Components.name(meta, Component.text("Confirm Deletion", NamedTextColor.RED, TextDecoration.BOLD));
            Components.lore(meta, Components.miniSplit(
                    """
                            <gray>Click to <red>delete</red> this tree.
                                            
                            <gray>This action <red>cannot</red> be undone.
                            """));
        });

        private static final ItemStack CANCEL_DELETION = ItemUtil.wrapEdit(new ItemStack(Material.REDSTONE_BLOCK), (meta) -> {
            Components.name(meta, Component.text("Cancel Deletion", NamedTextColor.GREEN, TextDecoration.BOLD));
            Components.lore(meta, Components.miniSplit(
                    """
                            <gray>Click to <green>cancel</green> this action.
                                            
                            <gray>No changes will be made.
                            """));
        });

        private static final Component TREE_DELETED = Components.prefixedSimpleMessage(Component.text("Your tree has been deleted!"));

        public ConfirmDeleteTree(OnlineTree tree) {
            super(3, HOLDER);
            this.setOnTopClick(event -> event.setCancelled(true));

            StaticPane pane = ItemUtil.wrapGui(this.getInventoryComponent());
            pane.addItem(
                    new GuiItem(CONFIRM_DELETION, (event) -> {
                        event.getWhoClicked().closeInventory();
                        TreeManager.INSTANCE.deleteTree(tree);
                        tree.getHeart().getBlock().setType(Material.AIR);

                        event.getWhoClicked().sendMessage(TREE_DELETED);
                        new PlayerLoseEvent((Player) event.getWhoClicked()).callEvent();
                    }), 2, 1
            );
            pane.addItem(
                    new GuiItem(CANCEL_DELETION, (event) -> {
                        event.getWhoClicked().closeInventory();
                    }), 6, 1
            );
        }

    }

}
