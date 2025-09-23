package com.stifflered.bartercontainer.util;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.InventoryComponent;
import com.github.stefvanschie.inventoryframework.gui.type.util.Gui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;

import me.sashak.inventoryutil.ItemGiver;
import me.sashak.inventoryutil.slotgroup.SlotGroups;

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

/**
 * Utility helpers for item editing, GUI scaffolding (InventoryFramework), and giving/dropping items.

 * Highlights:
 *  - wrapEdit: safe meta editing in a one-liner.
 *  - GUI helpers: wrap panes, back buttons, list items into panes.
 *  - giveItemOrThrow: tries to give items; if inventory full, drops at player’s feet (insta-pickup).
 *  - glow: applies a fake glow (hidden enchant).
 *  - subtract: decrements item only outside Creative mode.
 */
public class ItemUtil {

    /** A blank glass pane (black) with tooltip hidden—useful for GUI fillers. */
    public static final ItemStack BLANK = ItemUtil.wrapEdit(new ItemStack(Material.BLACK_STAINED_GLASS_PANE), (meta) -> meta.setHideTooltip(true));

    /** Standard “Back” button item used across GUIs (name/lore routed via messages.yml). */
    private static final ItemStack BACK = ItemUtil.wrapEdit(new ItemStack(Material.OAK_SIGN), (meta) -> {
        Components.name(meta, Messages.mm("util.back_button.name"));
        // Single-line lore; reuse helper to apply non-italic decoration
        Components.lore(meta, Messages.mm("util.back_button.lore"));
    });

    /** Edit an ItemStack’s ItemMeta via a Consumer, returning the same stack for chaining. */
    public static ItemStack wrapEdit(ItemStack itemStack, Consumer<ItemMeta> meta) {
        itemStack.editMeta(meta);
        return itemStack;
    }

    // ---------- InventoryFramework helpers ----------

    /** Wrap an InventoryComponent in a full-size StaticPane and return it (adds to component). */
    public static StaticPane wrapGui(InventoryComponent component) {
        return wrapGui(component, component.getLength(), component.getHeight());
    }

    /** Create and add a StaticPane of specific size to a component. */
    public static StaticPane wrapGui(InventoryComponent component, int length, int height) {
        StaticPane staticPane = new StaticPane(0, 0, length, height);
        component.addPane(staticPane);
        return staticPane;
    }

    /** Create and add a StaticPane at a specific offset and size to a component. */
    public static StaticPane wrapGui(InventoryComponent component, int x, int y, int length, int height) {
        StaticPane staticPane = new StaticPane(x, y, length, height);
        component.addPane(staticPane);
        return staticPane;
    }

    // ---------- Back button helpers ----------

    /** Back button that shows a newly supplied GUI when clicked. */
    public static GuiItem back(Supplier<Gui> gui) {
        return new GuiItem(BACK, (event) -> gui.get().show(event.getWhoClicked()));
    }

    /** Back button that returns to a pre-existing GUI instance. */
    public static GuiItem back(Gui gui) {
        return new GuiItem(BACK, (event) -> gui.show(event.getWhoClicked()));
    }

    /** Back button that attempts to return to the current inventory’s GUI holder, else shows a barrier. */
    public static GuiItem back(Inventory inventory) {
        if (inventory.getHolder() instanceof Gui gui) {
            return new GuiItem(BACK, (event) -> gui.show(event.getWhoClicked()));
        } else {
            return new GuiItem(new ItemStack(Material.BARRIER));
        }
    }

    // ---------- Pane helpers ----------

    /**
     * Lay out a list of GuiItems left-to-right, top-to-bottom across the pane.
     * Stops early if items run out.
     */
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

    // ---------- Give/drop helpers ----------

    /** Drop an item instantly at the player’s feet with zero pickup delay and ownership set. */
    public static void dropItemInstant(Player player, ItemStack giveItem) {
        Item item = player.getWorld().dropItem(player.getLocation(), giveItem);
        item.setOwner(player.getUniqueId());
        item.setPickupDelay(0);
    }

    /**
     * Try to give an item to the player’s main inventory.
     * If there’s overflow, swing main hand and drop extras at their feet.
     */
    public static void giveItemOrThrow(Player player, ItemStack giveItem) {
        List<ItemStack> extra = Objects.requireNonNullElse(ItemGiver.giveItems(player, SlotGroups.PLAYER_MAIN_INV, giveItem), List.of());

        if (!extra.isEmpty()) {
            player.swingMainHand();
            for (ItemStack itemStack : extra) {
                dropItemInstant(player, itemStack); // TODO: drop item api
            }
        }
    }

    /** True if the stack is null or air. */
    public static boolean isAirItem(@Nullable ItemStack itemStack) {
        return itemStack == null || itemStack.getType() == Material.AIR;
    }

    /** Apply a "glow" effect by adding a dummy enchant and hiding it from the tooltip. */
    public static void glow(ItemMeta meta) {
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addEnchant(Enchantment.LURE, 1, true);
    }

    /** Subtract one from the stack amount unless the player is in Creative. */
    public static void subtract(Player player, ItemStack itemStack) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            itemStack.subtract();
        }
    }
}
