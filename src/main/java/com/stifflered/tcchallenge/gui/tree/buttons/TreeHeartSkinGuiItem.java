package com.stifflered.tcchallenge.gui.tree.buttons;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.stifflered.tcchallenge.gui.common.SimplePaginator;
import com.stifflered.tcchallenge.tree.OnlineTree;
import com.stifflered.tcchallenge.tree.components.TreeComponent;
import com.stifflered.tcchallenge.tree.components.cosmetic.CosmeticStorage;
import com.stifflered.tcchallenge.tree.components.cosmetic.heartskin.HeartSkin;
import com.stifflered.tcchallenge.tree.components.cosmetic.heartskin.HeartSkinRegistry;
import com.stifflered.tcchallenge.util.Components;
import com.stifflered.tcchallenge.util.ItemUtil;
import com.stifflered.tcchallenge.util.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TreeHeartSkinGuiItem extends GuiItem {

    private static final ComponentHolder HEART_TYPE = ComponentHolder.of(Component.text("Heart Type"));
    private static final Component SKIN_LOCKED = Component.text("Locked", NamedTextColor.RED);
    private static final Component SKIN_SELECTED = Component.text("Click to Unselect", NamedTextColor.GREEN);

    public TreeHeartSkinGuiItem(OnlineTree tree) {
        super(ItemUtil.wrapEdit(new ItemStack(Material.ROOTED_DIRT), (meta) -> {
            Components.name(meta, Component.text("☐ Heart Type", TextColor.color(255, 109, 106)));
            Components.lore(meta, Components.miniSplit("""
                    <gray>Edit your tree heart's appearance.
                    """));
        }), (event) -> {
            open(event.getWhoClicked(), tree, event.getClickedInventory());
        });
    }

    public static void open(HumanEntity player, OnlineTree tree, @Nullable Inventory inventory) {
        List<GuiItem> items = new ArrayList<>();
        HeartSkin target = tree.getComponentStorage().get(TreeComponent.COSMETIC).getHeartSkin();

        for (HeartSkin heartSkin : HeartSkinRegistry.getSkinMap().values()) {
            ItemStack itemStack = ItemUtil.wrapEdit(new ItemStack(heartSkin.blockType()), (meta) -> {
                Components.name(meta, Component.text(heartSkin.blockType().name() + " Skin"));

                if (heartSkin == target) {
                    Components.lore(meta, SKIN_SELECTED);
                    ItemUtil.glow(meta);
                } else if (!player.hasPermission(heartSkin.permission())) {
                    Components.lore(meta, SKIN_LOCKED);
                }
            });

            GuiItem guiItem = new GuiItem(itemStack, (click) -> {
                CosmeticStorage cosmeticStorage = tree.getComponentStorage().get(TreeComponent.COSMETIC);
                if (player.hasPermission(heartSkin.permission()) || target == heartSkin) {
                    cosmeticStorage.setHeartSkin(target == heartSkin ? null : heartSkin);
                    tree.getHeartblock().forceTickSkin(tree, true);

                    Sounds.choose(player);
                    click.getWhoClicked().closeInventory();
                }
            });
            items.add(guiItem);
        }

        SimplePaginator paginator = new SimplePaginator(6, HEART_TYPE, items, inventory);
        paginator.show(player);
    }

}
