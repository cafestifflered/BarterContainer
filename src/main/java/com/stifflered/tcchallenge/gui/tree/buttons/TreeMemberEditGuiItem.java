package com.stifflered.tcchallenge.gui.tree.buttons;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.AnvilGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.stifflered.tcchallenge.BarterChallenge;
import com.stifflered.tcchallenge.gui.common.RotatingPane;
import com.stifflered.tcchallenge.tree.OnlineTree;
import com.stifflered.tcchallenge.tree.components.TreeComponent;
import com.stifflered.tcchallenge.tree.components.member.Member;
import com.stifflered.tcchallenge.tree.components.member.MemberStorage;
import com.stifflered.tcchallenge.tree.permission.TreeRole;
import com.stifflered.tcchallenge.util.Components;
import com.stifflered.tcchallenge.util.ItemUtil;
import com.stifflered.tcchallenge.util.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class TreeMemberEditGuiItem extends GuiItem {

    private static final ComponentHolder RENAME_EDITOR = ComponentHolder.of(
            Component.text("☻ Add Member")
    );

    private static final ItemStack CONFIRM_MEMBER = ItemUtil.wrapEdit(new ItemStack(Material.EMERALD_BLOCK), (meta) -> {
        Components.name(meta, Component.text("Confirm Member", NamedTextColor.GREEN, TextDecoration.BOLD));
        Components.lore(meta, Components.miniSplit(
                """
                        <gray>Click to <green>modify</green> this player.
                        """));
    });

    public TreeMemberEditGuiItem(OnlineTree tree) {
        super(ItemUtil.wrapEdit(new ItemStack(Material.PLAYER_HEAD), (meta) -> {
            Components.name(meta, RENAME_EDITOR.getComponent());
            Components.lore(meta, Components.miniSplit("""
                    <gray>Click to <green>manage</green> your tree's members.
                    """));
        }), (event) -> {
            event.getWhoClicked().closeInventory();

            openManagePlayerMenu((Player) event.getWhoClicked(), tree);
        });
    }

    public static CompletableFuture<Void> openManagePlayerMenu(Player player, OnlineTree tree) {
        CompletableFuture<Void> onFinish = new CompletableFuture<>();

        AnvilGui gui = new AnvilGui(RENAME_EDITOR);
        gui.setOnTopClick(e -> e.setCancelled(true));

        // Rename result slot
        StaticPane renamePane = ItemUtil.wrapGui(gui.getFirstItemComponent());
        {
            ItemStack renameItem = ItemUtil.wrapEdit(new ItemStack(Material.PLAYER_HEAD), (meta) -> {
                meta.displayName(Component.text("Edit Player"));
            });

            renamePane.addItem(new GuiItem(renameItem), 0, 0);
        }

        // Second slot
        RotatingPane<RoleSelect> optionSlot = new RotatingPane<>(gui, ItemUtil.wrapGui(gui.getSecondItemComponent()), 0, 0, RoleSelect.NO_PERMISSION, RoleSelect.values());
        MemberStorage storage = tree.getComponentStorage().get(TreeComponent.MEMBER);

        new BukkitRunnable() {

            private String lastName = gui.getRenameText();

            @Override
            public void run() {
                if (gui.getViewerCount() == 0) {
                    cancel();
                    return;
                }

                String currentText = gui.getRenameText();
                if (!this.lastName.equals(gui.getRenameText())) {
                    this.lastName = currentText;

                    // Rename result slot
                    {
                        ItemStack renameItem = ItemUtil.wrapEdit(new ItemStack(Material.PLAYER_HEAD), (meta) -> {
                            meta.displayName(Component.text(currentText));

                            Member member = storage.getMember(currentText);
                            if (member != null) {
                                optionSlot.setValue(RoleSelect.fromTreeRole(member.getMemberAccess()));
                            } else {
                                optionSlot.setValue(RoleSelect.NO_PERMISSION);
                            }

                        });

                        renamePane.addItem(new GuiItem(renameItem), 0, 0);
                    }
                    gui.update();
                }
            }
        }.runTaskTimer(BarterChallenge.INSTANCE, 0, 1);


        // Rename confirm
        {
            StaticPane pane = ItemUtil.wrapGui(gui.getResultComponent());
            pane.addItem(new GuiItem(CONFIRM_MEMBER, (rename) -> {
                new BukkitRunnable() {

                    @Override
                    public void run() {

                        PlayerProfile playerProfile = Bukkit.createProfile(gui.getRenameText());
                        if (playerProfile.complete()) {
                            new BukkitRunnable() {

                                @Override
                                public void run() {
                                    TreeRole role = RoleSelect.toTreeRole(optionSlot.getValue());
                                    if (role == null) {
                                        storage.removeMember(playerProfile.getId());
                                    } else {
                                        Member member = new Member(playerProfile, role, null);
                                        storage.add(member);
                                    }

                                    Sounds.writeText(player);
                                }
                            }.runTask(BarterChallenge.INSTANCE);
                        }

                    }
                }.runTaskAsynchronously(BarterChallenge.INSTANCE);

                player.closeInventory();
                onFinish.complete(null);
            }), 0, 0);
        }

        gui.show(player);

        return onFinish;
    }

    enum RoleSelect implements RotatingPane.IterableValue {
        NO_PERMISSION(
                ItemUtil.wrapEdit(new ItemStack(Material.BARRIER), (meta) -> {
                    Components.name(meta, Component.text("No Permission", NamedTextColor.RED));
                    Components.lore(meta, Components.miniSplit(
                            """
                                    <gray>Removes any permissions for the given player.
                                    """));
                })
        ),
        VISITOR(ItemUtil.wrapEdit(new ItemStack(Material.PLAYER_HEAD), (meta) -> {
            Components.name(meta, Component.text("Visitor", NamedTextColor.GREEN));
            Components.lore(meta, Components.miniSplit(
                    """
                            <gray>Gives a player permission to <green>walk</green> on your roots.
                            """));
        })),
        PLANTER(ItemUtil.wrapEdit(new ItemStack(Material.FLOWERING_AZALEA_LEAVES), (meta) -> {
            Components.name(meta, Component.text("Planter", NamedTextColor.DARK_GREEN));
            Components.lore(meta, Components.miniSplit(
                    """
                            <gray>Gives a player permission to <green>plant</green> new roots.
                            <gray>Also has all permissions as Visitor.
                            """));
        })),
        ADMIN(ItemUtil.wrapEdit(new ItemStack(Material.COMMAND_BLOCK), (meta) -> {
            Components.name(meta, Component.text("Admin", NamedTextColor.GOLD));
            Components.lore(meta, Components.miniSplit(
                    """
                            <gray>Gives a player permission to <green>edit</green> your tree.
                            <gray>Also has all permissions as Visitor & Planter.
                            """));
        }))
        ;


        private final ItemStack itemStack;

        RoleSelect(ItemStack itemStack) {
            this.itemStack = itemStack;
        }

        @Override
        public @NotNull ItemStack getItem() {
            return this.itemStack;
        }

        public static RoleSelect fromTreeRole(@NotNull TreeRole treeRole) {
            return switch (treeRole) {
                case VISITOR -> RoleSelect.VISITOR;
                case PLANTER -> RoleSelect.PLANTER;
                case ADMIN -> RoleSelect.ADMIN;
            };
        }

        @Nullable
        public static TreeRole toTreeRole(@NotNull RoleSelect treeRole) {
            return switch (treeRole) {
                case NO_PERMISSION -> null;
                case VISITOR -> TreeRole.VISITOR;
                case PLANTER -> TreeRole.PLANTER;
                case ADMIN -> TreeRole.ADMIN;
            };
        }
    }
}
