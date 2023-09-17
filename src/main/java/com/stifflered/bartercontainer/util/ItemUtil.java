package com.stifflered.bartercontainer.util;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.InventoryComponent;
import com.github.stefvanschie.inventoryframework.gui.type.util.Gui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import me.sashak.inventoryutil.ItemGiver;
import me.sashak.inventoryutil.slotgroup.SlotGroups;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ItemUtil {

    private static final ItemStack BACK = ItemUtil.wrapEdit(new ItemStack(Material.OAK_SIGN), (meta) -> {
        Components.name(meta, Component.text("← Back", NamedTextColor.AQUA));
        Components.lore(meta, Components.mini("<gray>Click to go back."));
    });

    public static ItemStack wrapEdit(ItemStack itemStack, Consumer<ItemMeta> meta) {
        itemStack.editMeta(meta);
        return itemStack;
    }

    // Panes are annoying, wrap it
    public static StaticPane wrapGui(InventoryComponent component) {
        return wrapGui(component, component.getLength(), component.getHeight());
    }

    public static StaticPane wrapGui(InventoryComponent component, int length, int height) {
        StaticPane staticPane = new StaticPane(0, 0, length, height);
        component.addPane(staticPane);
        return staticPane;
    }

    public static StaticPane wrapGui(InventoryComponent component, int x, int y, int length, int height) {
        StaticPane staticPane = new StaticPane(x, y, length, height);
        component.addPane(staticPane);
        return staticPane;
    }


    public static GuiItem back(Supplier<Gui> gui) {
        return new GuiItem(BACK, (event) -> {
            gui.get().show(event.getWhoClicked());
        });
    }

    public static GuiItem back(Gui gui) {
        return new GuiItem(BACK, (event) -> {
            gui.show(event.getWhoClicked());
        });
    }

    public static GuiItem back(Inventory inventory) {
        if (inventory.getHolder() instanceof Gui gui) {
            return new GuiItem(BACK, (event) -> {
                gui.show(event.getWhoClicked());
            });
        } else {
            return new GuiItem(new ItemStack(Material.BARRIER));
        }
    }

    public static StaticPane listItems(StaticPane pane, List<GuiItem> item) {
        Deque<GuiItem> queue = new ArrayDeque<>(item);

        for (int height = 0; height < pane.getHeight(); height++) {
            for (int length = 0; length < pane.getLength(); length++) {
                if (queue.isEmpty()) {
                    return pane;
                }
                GuiItem popped = queue.pop();

                pane.addItem(popped, length, height);
            }
        }

        return pane;
    }

    public static void dropItemInstant(Player player, ItemStack giveItem) {
        Item item = player.getWorld().dropItem(player.getLocation(), giveItem);
        item.setOwner(player.getUniqueId());
        item.setPickupDelay(0);
    }

    public static void giveItemOrThrow(Player player, ItemStack giveItem) {
        List<ItemStack> extra = Objects.requireNonNullElse(ItemGiver.giveItems(player, SlotGroups.PLAYER_MAIN_INV, giveItem), List.of());

        if (!extra.isEmpty()) {
            player.swingMainHand();
            for (ItemStack itemStack : extra) {
                dropItemInstant(player, itemStack); // TODO: drop item api
            }
        }
    }

    public static boolean isAirItem(@Nullable ItemStack itemStack) {
        return itemStack == null || itemStack.getType() == Material.AIR;
    }

    public static void glow(ItemMeta meta) {
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addEnchant(Enchantment.LURE, 1, true);
    }

    public static void subtract(Player player, ItemStack itemStack) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            itemStack.subtract();
        }
    }
}
