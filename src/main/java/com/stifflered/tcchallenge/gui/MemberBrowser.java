package com.stifflered.tcchallenge.gui;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.Pane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.stifflered.tcchallenge.BarterChallenge;
import com.stifflered.tcchallenge.tree.OnlineTree;
import com.stifflered.tcchallenge.util.Components;
import com.stifflered.tcchallenge.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class MemberBrowser extends ChestGui {

    private static final ComponentHolder HOLDER = ComponentHolder.of(
            Component.text("Are you sure?")
    );


    private static final ItemStack INVITE = ItemUtil.wrapEdit(new ItemStack(Material.ANVIL), (meta) -> {
        Components.name(meta, Component.text("Invite", NamedTextColor.AQUA, TextDecoration.BOLD));
        Components.lore(meta, Components.miniSplit(
                """
                        <gray><aqua>Click</aqua> to invite a player to
                        your tree.
                        """));
    });

    private static final Component TREE_DELETED = Components.prefixedSimpleMessage(Component.text("Your tree has been deleted!"));


    private final BarterChallenge plugin;

    public MemberBrowser(Player p, BarterChallenge plugin, OnlineTree tree) {
        super(6, HOLDER);

        this.plugin = plugin;
//paginatedPane
//        PaginatedPane paginatedPane = new PaginatedPane(0, 0, 5, 5);
//        paginatedPane.populateWithGuiItems(previousMenu.getTree()
//                .getMembers()
//                .getStream()
//                .filter(m -> m.getMemberType() != MemberType.OWNER)
//                .map((member) -> {
//                    ItemStack item = new ItemStack(Material.PLAYER_HEAD);
//                    item.editMeta((meta) -> {
//                        Components.name(meta, Component.text(member.getPlayerName()));
//                    });
//
//                    return new GuiItem(item, (click) -> {
//                        if (click.getClick().isLeftClick()) {
//                            HopperGui gui = new HopperGui(ComponentHolder.of(Component.text("Change Group: " + member.getPlayerName())));
//                            StaticPane pane = ItemUtil.wrapGui(gui.getSlotsComponent());
//
//                            int i = 0;
//                            for (MemberType memberType : MemberType.values()) {
//                                pane.addItem(new GuiItem(ItemUtil.wrapEdit(new ItemStack(Material.STONE), (meta) -> {
//                                    meta.displayName(Component.text(memberType.getName()));
//                                }), (inventoryClickEvent) -> {
//                                    member.setMemberType(memberType);
//                                    member.save();
//                                    MemberBrowser.this.show(inventoryClickEvent.getWhoClicked());
//                                }), i, 0);
//                                i++;
//                            }
//                        }
//                    });
//                }).toList());
        StaticPane pane = new StaticPane(0, 5, 8, 1, Pane.Priority.HIGH);
        // pane.addItem(ItemUtil.back(previousMenu), 4, 0);


        this.addPane(pane);
        //this.addPane(paginatedPane);
//        pane.addItem(
//                new GuiItem(INVITE, (event) -> {
//                    event.getWhoClicked().closeInventory();
//                    MemberBrowser.this.openGUI();
//                    plugin.getInvitationManager().createInvite(new Invite(target, tree, p, type));
//                }), 8, 0
//
//        );

//        if (plugin.getInvitationManager().isInvited(target, tree)) {
//            return false;
//        }
//
//        return tree.getRelationship(target.getUniqueId()) == null && !p.equals(target);
//        openGUI();
    }

    // return tree.getMembers().getStream()
    //                .filter(m -> m.getMemberType() != MemberType.OWNER)
    //                .toArray(Member[]::new);

//    @Override
//    public GUIItem getButton(Member member) {
//        CustomItem item = new CustomItem(XMaterial.PLAYER_HEAD)
//                .skull(member.getPlayerName())
//                .name(new WaveEffect("§b§l", "§7§l", 2, member.getPlayerName()).getCurrentFrame())
//                .lore(
//                        "§bLeft Click §7to modify group",
//                        "§bRight Click §7to delete Member",
//                        "",
//                        "§7Group: §e" + member.getMemberType().getName(),
//                        "§7Expiring: §e" + (member.getExpiring() != 0 ? Tools.getTimeString(member.getExpiring() - System.currentTimeMillis(), TimeUnit.MILLISECONDS) : "Permanent") + " §7(§bHover §7+ §bQ§7)"
//                );
//
//        return new ClickableGUIItem(item) {
//            @Override
//            public void onClick(Player p, ActionType type) {
//                if (type == ActionType.LEFT) {
//                    new MemberTypeChooser(p, plugin, "Change Group > " + member.getPlayerName(), new Callback<Player>() {
//                        @Override
//                        public void run(Player o) {
//                            new MemberBrowser(o, plugin, tree, fromTree);
//                        }
//                    }) {
//                        @Override
//                        public void choose(MemberType type) {
//
//                        }
//                    };
//                }
//
//                if (type == ActionType.RIGHT) {
//                    member.delete();
//                }
//
//                if (type == ActionType.Q) {
//                    new SetTimeMenu(p, plugin, "Expiring >", "Permanent", true) {
//                        @Override
//                        public void onResult(long time) {
//                            member.setExpiring(System.currentTimeMillis() + time * 1000);
//                            member.save();
//
//                            MemberBrowser.this.openGUI();
//                        }
//                    };
//                }
//            }
//        };
}
