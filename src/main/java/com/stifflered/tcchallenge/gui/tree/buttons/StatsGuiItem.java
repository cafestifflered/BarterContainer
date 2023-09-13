package com.stifflered.tcchallenge.gui.tree.buttons;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.stifflered.tcchallenge.tree.OnlineTree;
import com.stifflered.tcchallenge.tree.components.StatsStorage;
import com.stifflered.tcchallenge.tree.components.TreeComponent;
import com.stifflered.tcchallenge.util.Components;
import com.stifflered.tcchallenge.util.ItemUtil;
import com.stifflered.tcchallenge.util.StringUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

public class StatsGuiItem extends GuiItem {
    public StatsGuiItem(OnlineTree tree) {
        super(ItemUtil.wrapEdit(new ItemStack(Material.CREEPER_BANNER_PATTERN), (meta) -> {
            meta.addItemFlags(ItemFlag.values());

            Components.name(meta, Component.text("ℹ Statistics", NamedTextColor.GRAY));

            StatsStorage statsStorage = tree.getComponentStorage().get(TreeComponent.STATS);
            Components.lore(meta, Components.miniSplit("""
                            <white>Root Count (Estimate):</white> <gray><roots>
                            <white>Creation Date:</white> <gray><created>
                            <white>Total Playtime:</white> <gray><playtime>
                            <white>Current Session Playtime:</white> <gray><current_playtime>
                            """,
                    Placeholder.parsed("roots", String.valueOf(statsStorage.getRoots())),
                    Placeholder.parsed("created", String.valueOf(statsStorage.getCreated())),
                    Placeholder.parsed("playtime", StringUtil.formatMilliDuration(statsStorage.getTotalPlayTime(), true)),
                    Placeholder.parsed("current_playtime", StringUtil.formatMilliDuration(statsStorage.getCurrentSessionTime(), true))
            ));
        }));
    }


}
