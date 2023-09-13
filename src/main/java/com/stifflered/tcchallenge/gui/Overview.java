package com.stifflered.tcchallenge.gui;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.stifflered.tcchallenge.util.Components;
import com.stifflered.tcchallenge.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class Overview extends ChestGui {

    private static final ComponentHolder HOLDER = ComponentHolder.of(
            Component.text("TCChallenge Hub")
    );


    private static final ItemStack INVITE = ItemUtil.wrapEdit(new ItemStack(Material.ANVIL), (meta) -> {
        Components.name(meta, Component.text("Invite", NamedTextColor.AQUA, TextDecoration.BOLD));
        Components.lore(meta, Components.miniSplit(
                """
                        <gray><aqua>Click</aqua> to invite a player to
                        your tree.
                        """));
    });


    public Overview() {
        super(6, HOLDER);

        StaticPane pane = ItemUtil.wrapGui(this.getInventoryComponent());


    }

//    @Override
//    public void onEvent(GUIEvent event) {
//
//    }
//
//    @Override
//    public int getCurrentSlots() {
//        return 54;
//    }
//
//    @Override
//    public String getCurrentTitle() {
//        return "TCChallenge "+plugin.getVersion();
//    }
//
//    @Override
//    public GUIItem[] getGUIItems() {
//        HashSet<GUIItem> items = new HashSet<>();
//
//        items.add(new ClickableGUIItem(getCreateAdminTreeButton(), 41) {
//            @Override
//            public void onClick(Player p, ActionType type) {
//                if(!plugin.isChallengeWorld(p.getWorld())){
//                    p.sendMessage(plugin.getPrefix()+"§7You are not in the right world");
//                    return;
//                }
//
//                plugin.newTree(p.getLocation(), WoodType.OAK).create();
//                p.sendMessage(plugin.getPrefix()+"§7Woooosh, admin tree is created");
//            }
//        });
//
//        items.add(new ClickableGUIItem(getTreeListButton(plugin.getTrees().get().length), 23) {
//            @Override
//            public void onClick(Player p, ActionType type) {
//                new TreeBrowser(p, plugin);
//            }
//        });
//
//        items.add(new ClickableGUIItem(getMySQLSettingsButton(), 20) {
//            @Override
//            public void onClick(Player p, ActionType type) {
//                new TCCSettings(p, plugin){
//                    @Override
//                    public void onBack() {
//                        Overview.this.openGUI();
//                    }
//                };
//            }
//        });
//
//        items.add(new ClickableGUIItem(getWorldSettingButton(plugin.getChallengeWorldPrefix()), 26) {
//            @Override
//            public void onClick(Player p, ActionType type) {
//                new UserInput(p, plugin, "§bSet World Prefix", "§7Please type in the new prefix in chat"){
//                    @Override
//                    public boolean onResult(String result) {
//                        plugin.setChallengeWorldPrefix(result);
//                        Overview.this.openGUI();
//                        return true;
//                    }
//                };
//            }
//        });
//
//        return items.toArray(new GUIItem[items.size()]);
//    }
//
//    private CustomItem getTreeListButton(int amount){
//        WoodType[] types = WoodType.values();
//        int current = (int) (System.currentTimeMillis()/1500 % types.length);
//
//        CustomItem item = new CustomItem(types[current].getSapling())
//                .name(new WaveEffect("§a§l", "§7§l", 3, "Tree List").getCurrentFrame())
//                .lore(
//                        "§7Click to show all Trees",
//                        "",
//                        "§7Amount: §e"+amount
//                );
//
//        return item;
//    }
//
//    private CustomItem getMySQLSettingsButton(){
//        CustomItem item = new CustomItem(XMaterial.ENDER_CHEST)
//                .name(new WaveEffect("§a§l", "§f§l", 3, "MySQL").getCurrentFrame())
//                .lore(
//                        "§7Click to view MySQL Settings"
//                );
//
//        return item;
//    }
//
//    private CustomItem getWorldSettingButton(String worldPrefix){
//        CustomItem item = new CustomItem(XMaterial.END_PORTAL_FRAME)
//                .name(new WaveEffect("§b§l", "§f§l", 3, "Set World").getCurrentFrame())
//                .lore(
//                        "§7Click to change World Prefix",
//                        "",
//                        "§7World Prefix: §e"+worldPrefix
//                );
//
//        return item;
//    }
//
//    private CustomItem getCreateAdminTreeButton(){
//        CustomItem item = new CustomItem(XMaterial.ACACIA_SAPLING)
//                .name(new WaveEffect("§b§L", "§f§l", 3, "Create Admin Tree").getCurrentFrame())
//                .lore(
//                        "§7Click to create an Admin Tree",
//                        "§7on your current Location"
//                );
//
//        return item;
//    }
}
