package com.stifflered.bartercontainer.gui.tree;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.stifflered.bartercontainer.gui.tree.buttons.SetPriceGuiItem;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.stifflered.bartercontainer.util.Messages;
import com.stifflered.bartercontainer.util.Sounds;
import me.sashak.inventoryutil.ItemRemover;
import me.sashak.inventoryutil.slotgroup.SlotGroups;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class BarterBuyGui extends ChestGui {

    private static final ItemStack BUY_ITEM = ItemUtil.wrapEdit(new ItemStack(Material.LIME_CANDLE), (meta) -> {
        Components.name(meta, Component.text("◎ Open Shop", TextColor.color(0, 255, 0)));
        Components.lore(meta, Components.miniSplit("""
                    <gray>Click an item to <green>buy</green>.""")
        );
    });

    private static final ItemStack OUT_OF_STOCK = ItemUtil.wrapEdit(new ItemStack(Material.RED_CANDLE), (meta) -> {
        Components.name(meta, Component.text("◎ Out of Stock", TextColor.color(255, 0, 0)));
        Components.lore(meta, Components.miniSplit("""
                    <gray>This shop is <red>out</red> of stock!""")
        );
    });

    private static final ItemStack SHOP_FULL = ItemUtil.wrapEdit(new ItemStack(Material.RED_CANDLE), (meta) -> {
        Components.name(meta, Component.text("◎ Full Shop", TextColor.color(255, 0, 0)));
        Components.lore(meta, Components.miniSplit("""
                    <gray>This shop's bank is <red>full</red>!""")
        );
    });

    private static final ItemStack NOT_ENOUGH_PRICE = ItemUtil.wrapEdit(new ItemStack(Material.RED_CANDLE), (meta) -> {
        Components.name(meta, Component.text("◎ Can't afford!", TextColor.color(255, 0, 0)));
        Components.lore(meta, Components.miniSplit("""
                    <gray>You do not have the required
                    <gray><red>items</red> for this store!""")
        );
    });

    public BarterBuyGui(Player player, BarterStore store) {
        super(5, ComponentHolder.of(store.getNameStyled()));
        this.setOnGlobalClick((event) -> {
            event.setCancelled(true);
        });

        StaticPane pane = ItemUtil.wrapGui(this.getInventoryComponent(), 9, 5);

        for (int i = 0; i < 9; i++) {
            GuiItem item = new GuiItem(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), null);
            pane.addItem(item, i, 0);
            pane.addItem(item, i, 1);
        }

        pane.addItem(new GuiItem(SetPriceGuiItem.getPriceItem(store)),4, 0);
       // pane.addItem(new GuiItem(BUY_ITEM_ARROW),0, 1);

        StaticPane itemDisplay = ItemUtil.wrapGui(this.getInventoryComponent(), 0, 2, 9, 3);
        {
            ItemUtil.listItems(itemDisplay, getBuyItems(store).stream().filter(Objects::nonNull).map(buySlot -> new GuiItem(buySlot.item, mainBuyClick -> {
                Sounds.choose(player);
                ItemStack previewItem = Objects.requireNonNullElse(store.getSaleStorage().getItem(buySlot.slot()), new ItemStack(Material.AIR)).clone();
                pane.addItem(new GuiItem(ItemUtil.wrapEdit(previewItem.clone(), (meta) -> {
                    Components.name(meta, Component.text("☑ Confirm Purchase", TextColor.color(0, 255, 0), TextDecoration.BOLD, TextDecoration.UNDERLINED));
                    Components.lore(meta, Components.miniSplit("""
                                <gray>Click to <green>confirm</green>
                                <gray>your purchase.""")
                    );
                    ItemUtil.glow(meta);
                }), (event) -> {

                    ItemStack itemStack = Objects.requireNonNullElse(store.getSaleStorage().getItem(buySlot.slot()), new ItemStack(Material.AIR)).clone();
                    if (!previewItem.equals(itemStack)) {
                        player.sendMessage(Components.prefixedError(Component.text("Looks like someone already took this item!")));
                        player.closeInventory();
                        return;
                    }
                    Sounds.purchase(player);
                    if (me.sashak.inventoryutil.ItemUtil.hasAllItems(player, SlotGroups.PLAYER_ENTIRE_INV, store.getCurrentItemPrice())) {
                        HashMap<Integer, ItemStack> leftOver = store.getCurrencyStorage().addItem(store.getCurrentItemPrice());
                        if (leftOver.isEmpty()) {
                            ItemRemover.removeItems(player, SlotGroups.PLAYER_ENTIRE_INV, store.getCurrentItemPrice());
                            store.getSaleStorage().setItem(buySlot.slot, null);

                            ItemUtil.giveItemOrThrow(player, itemStack);
                        }
                        new BarterBuyGui(player, store).show(event.getWhoClicked());
                    }
                }), 4, 1);

                BarterBuyGui.this.show(mainBuyClick.getWhoClicked());
            })).toList());
        }

        boolean hasSlots = me.sashak.inventoryutil.ItemUtil.hasAllItems(player, SlotGroups.PLAYER_ENTIRE_INV, store.getCurrentItemPrice());
        if (!hasSlots) {
            pane.addItem(new GuiItem(NOT_ENOUGH_PRICE), 4, 1);
            return;
        }
        boolean hasRoom = me.sashak.inventoryutil.ItemUtil.hasRoomForItems(store.getCurrencyStorage(), SlotGroups.ENTIRE_INV, store.getCurrentItemPrice());
        if (!hasRoom) {
            pane.addItem(new GuiItem(SHOP_FULL), 4, 1);
            return;
        }

        boolean isEmpty = store.getSaleStorage().isEmpty();
        if (isEmpty) {
            pane.addItem(new GuiItem(OUT_OF_STOCK), 4, 1);
            return;
        }

        pane.addItem(new GuiItem(BUY_ITEM), 4, 1);
    }

    public static List<BuySlot> getBuyItems(BarterStore store) {
        List<BuySlot> itemStacks = new ArrayList<>();

        ListIterator<ItemStack> iterator = store.getSaleStorage().iterator();
        while (iterator.hasNext()) {
            int slot = iterator.nextIndex();
            ItemStack itemStack = iterator.next();
            if (itemStack != null) {
                itemStacks.add(new BuySlot(slot, itemStack.clone()));
            }
        }

        return itemStacks;
    }

    record BuySlot(int slot, ItemStack item) {

    }
}
