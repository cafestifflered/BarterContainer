package com.stifflered.bartercontainer.gui.tree;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.gui.tree.buttons.SetPriceGuiItem;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.BarterContainerLogger;
import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.stifflered.bartercontainer.util.Sounds;
import me.sashak.inventoryutil.ItemRemover;
import me.sashak.inventoryutil.slotgroup.SlotGroups;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class BarterBuyGui extends ChestGui {

    private static final BarterContainerLogger LOGGER = new BarterContainerLogger();

    private static final ItemStack BUY_ITEM = BarterContainer.INSTANCE
            .getConfiguration()
            .getBuyItemConfiguration();

    private static final ItemStack OUT_OF_STOCK = BarterContainer.INSTANCE
            .getConfiguration()
            .getOutOfStockItemConfiguration();

    private static final ItemStack SHOP_FULL = BarterContainer.INSTANCE
            .getConfiguration()
            .getShopFullItemConfiguration();

    private static final ItemStack NOT_ENOUGH_PRICE = BarterContainer.INSTANCE
            .getConfiguration()
            .getNotEnoughPriceItemConfiguration();

    private boolean canBuy = false;

    public BarterBuyGui(Player player, BarterStore store) {
        super(5, ComponentHolder.of(store.getNameStyled()));
        this.setOnGlobalClick((event) -> {
            event.setCancelled(true);
        });

        StaticPane pane = ItemUtil.wrapGui(this.getInventoryComponent(), 9, 5);
        for (int i = 0; i < 9; i++) {
            GuiItem item = new GuiItem(new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
            pane.addItem(item, i, 0);
            pane.addItem(item, i, 1);
        }

        pane.addItem(new GuiItem(SetPriceGuiItem.getPriceItem(store)),4, 0);
       // pane.addItem(new GuiItem(BUY_ITEM_ARROW),0, 1);

        StaticPane itemDisplay = ItemUtil.wrapGui(this.getInventoryComponent(), 0, 2, 9, 3);
        {
            ItemUtil.listItems(itemDisplay, getBuyItems(store).stream().filter(Objects::nonNull).map(buySlot -> new GuiItem(buySlot.item, mainBuyClick -> {
                if (!this.canBuy) {
                    return;
                }
                Sounds.choose(player);
                ItemStack previewItem = Objects.requireNonNullElse(store.getSaleStorage().getItem(buySlot.slot()), new ItemStack(Material.AIR)).clone();
                pane.addItem(new GuiItem(ItemUtil.wrapEdit(previewItem.clone(), (meta) -> {
                    Components.name(meta, Component.text("☑ Confirm Purchase", TextColor.color(0, 255, 0), TextDecoration.BOLD, TextDecoration.UNDERLINED));
                    Components.lore(meta, Components.miniSplit("""
                                <gray>Click to <green>confirm</green>
                                <gray>your purchase.""")
                    );
                    ItemUtil.glow(meta);
                }), (event) -> this.buy((Player) event.getWhoClicked(), previewItem, buySlot, store)), 4, 1);
                BarterBuyGui.this.update();
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
        this.canBuy = true;
    }


    private void buy(Player player, ItemStack previewItem, BuySlot buySlot, BarterStore store) {
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
                LOGGER.logTransaction(player, itemStack, store);
                Player owner = Bukkit.getPlayer(store.getPlayerProfile().getId());
                if (owner != null) {
                    owner.sendRichMessage(BarterContainer.INSTANCE.getConfiguration().getPurchaseFromPlayerMessage(),
                            Placeholder.parsed("count", String.valueOf(itemStack.getAmount())),
                            Placeholder.parsed("type", itemStack.getType().toString()),
                            Placeholder.parsed("purchaser", player.getName())
                    );
                }

                Component message = switch (BarterContainer.INSTANCE.getShoppingListManager().receive(player, itemStack)) {
                    case MODIFIED -> BarterContainer.INSTANCE.getConfiguration().getShoppingListProgress();
                    case REMOVED -> BarterContainer.INSTANCE.getConfiguration().getShoppingListCheckedOff();
                    case NOTHING -> null;
                };
                if (message != null) {
                    player.sendMessage(message);
                }


                BarterManager.INSTANCE.save(store);
            }
            new BarterBuyGui(player, store).show(player);
        }
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
