package com.stifflered.tcchallenge.gui.tree.buttons;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.stifflered.tcchallenge.gui.common.SimpleListGui;
import com.stifflered.tcchallenge.listeners.abilities.TreeAbility;
import com.stifflered.tcchallenge.tree.OnlineTree;
import com.stifflered.tcchallenge.tree.abilities.AbilityManager;
import com.stifflered.tcchallenge.tree.type.TreeType;
import com.stifflered.tcchallenge.util.Components;
import com.stifflered.tcchallenge.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TreePerksGuiItem extends GuiItem {

    private static final Component ACTIVE_ABILITIES = Component.text("Active Abilities");

    public TreePerksGuiItem(OnlineTree tree) {
        super(ItemUtil.wrapEdit(new ItemStack(Material.FIRE_CHARGE), (meta) -> {
            Components.name(meta, Component.text("♤ Perks", TextColor.color(255, 109, 106)));
            Components.lore(meta, Components.miniSplit("""
                    <gray>View tree perks and abilities.
                    """));
        }), (event) -> {
            open(event.getWhoClicked(), tree.getType(), event.getClickedInventory());
        });
    }

    public static void open(HumanEntity player, TreeType type, @Nullable Inventory inventory) {
        new SimpleListGui(getItems(type), ACTIVE_ABILITIES, inventory).show(player);
    }


    public static List<GuiItem> getItems(TreeType type) {
        List<GuiItem> items = new ArrayList<>();
        for (TreeAbility ability : AbilityManager.fromType(type)) {
            items.add(new GuiItem(ability.getAbilityIcon()));
        }
        return items;
    }

}
