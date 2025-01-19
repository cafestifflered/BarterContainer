package com.stifflered.bartercontainer.command;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.mojang.brigadier.*;
import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.gui.catalogue.*;
import com.stifflered.bartercontainer.gui.common.SimplePaginator;
import com.stifflered.bartercontainer.player.*;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.*;
import net.kyori.adventure.inventory.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.event.*;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShoppingListCommand extends BukkitCommand {

    private final ShoppingListManager manager = BarterContainer.INSTANCE.getShoppingListManager();;

    public ShoppingListCommand() {
        super("shoppinglist");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        Player player = (Player) sender;
        if (!sender.hasPermission("barterchests.shopping-list")) {
            sender.sendMessage(Components.NO_PERMISSION);
            return true;
        }
        // TODO: clean this up
        ShoppingListManager.ShoppingList.ShoppingListEntry specialAddEntry = new ShoppingListManager.ShoppingList.ShoppingListEntry(null, null);


        List<ShoppingListManager.ShoppingList.ShoppingListEntry> entries = new ArrayList<>();
        entries.addAll(manager.getShoppingList(player).toDisplayList());
        entries.add(specialAddEntry);

        ListPaginator<ShoppingListManager.ShoppingList.ShoppingListEntry> entryListPaginator = new ListPaginator<>(entries, 13);
        List<Component> pages = new ArrayList<>();
        for (int i = 0; i < entryListPaginator.getTotalPages(); i++) {
            TextComponent.Builder builder = Component.text();
            for (ShoppingListManager.ShoppingList.ShoppingListEntry record : entryListPaginator.getPage(i)) {
                if (record != specialAddEntry) {
                    builder.append(record.styled().append(Component.space().append(BarterContainer.INSTANCE.getConfiguration().getShoppingListX()).clickEvent(ClickEvent.callback(audience -> {
                        audience.sendMessage(
                                MiniMessage.miniMessage().deserialize(
                                        BarterContainer.INSTANCE.getConfiguration().getShoppingListRemoveItemMessage(),
                                        Placeholder.parsed("amount", "all"),
                                        Placeholder.parsed("item", record.itemStack().getType().key().asString())
                                )
                        );
                        manager.removeItem((Player) audience, record.itemStack());
                    })))).appendNewline();
                } else {
                    builder.append(BarterContainer.INSTANCE.getConfiguration().getShoppingListPlus()).clickEvent(ClickEvent.callback(audience -> {
                        if (audience instanceof Player player1) {
                            player1.closeInventory();
                            new ShoppingListGui(player1).show(player1);
                        }
                    })).appendNewline();
                }
            }
            pages.add(builder.build());
        }

        player.openBook(Book.book(Component.text("Data"), Component.text("?"), pages));

        return true;
    }
}
