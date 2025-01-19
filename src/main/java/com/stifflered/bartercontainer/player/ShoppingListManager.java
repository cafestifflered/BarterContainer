package com.stifflered.bartercontainer.player;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.gui.catalogue.*;
import com.stifflered.bartercontainer.util.BarterShopOwnerLogManager;
import com.stifflered.bartercontainer.util.ListPaginator;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.audience.*;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.*;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.*;

import java.util.*;


public class ShoppingListManager implements Listener {

    private static final NamespacedKey KEY = new NamespacedKey(BarterContainer.INSTANCE, "shopping-list");
    private static final NamespacedKey KEY_DATA = new NamespacedKey(BarterContainer.INSTANCE, "-data");

    private final JavaPlugin plugin;
    private final Map<Player, ShoppingList> shoppingLists = new HashMap<>();

    public ShoppingListManager(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        loadShoppingList(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        saveShoppingList(event.getPlayer());
        shoppingLists.remove(event.getPlayer());
    }

    public record ShoppingList(Map<ItemStack, Integer> items) {

        public void addItem(ItemStack itemStack, int amount) {
            this.items.put(itemStack, this.items.getOrDefault(itemStack, 0) + amount);
        }

        public void removeItem(ItemStack material) {
            this.items.remove(material);
        }

        /**
         * Decrement needed amount for the given material by 'amountReceived'.
         * Clamps to zero (does not remove).
         */
        public MutateState receiveItem(ItemStack material, int amountReceived) {
            if (items.containsKey(material)) {
                int current = items.get(material);
                int updated = Math.max(current - amountReceived, 0);
                if (updated == 0) {
                    removeItem(material);
                    return MutateState.REMOVED;
                } else {
                    items.put(material, updated);
                    return MutateState.MODIFIED;
                }
            }

            return MutateState.NOTHING;
        }

        public List<ShoppingListEntry> toDisplayList() {
            List<ShoppingListEntry> display = new ArrayList<>();
            for (Map.Entry<ItemStack, Integer> e : items.entrySet()) {
                display.add(new ShoppingListEntry(Component.translatable(e.getKey()).append(Component.text(" x" + e.getValue())), e.getKey()));
            }


            return display;
        }

        public record ShoppingListEntry(Component styled, ItemStack itemStack) {

        }
    }

    public ShoppingList getShoppingList(Player player) {
        return this.shoppingLists.get(player);
    }

    public void addItem(Player player, ItemStack material, int amount) {
        ShoppingList list = getShoppingList(player);
        list.addItem(material, amount);
    }

    public void removeItem(Player player, ItemStack material) {
        ShoppingList list = getShoppingList(player);
        list.removeItem(material);
    }

    public MutateState receive(Player player, ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isEmpty()) {
            return MutateState.NOTHING;
        }

        ShoppingList list = getShoppingList(player);
        return list.receiveItem(itemStack, itemStack.getAmount());
    }

    public MutateState receive(Player player, ItemStack itemStack, int amount) {
        if (itemStack == null || itemStack.getType().isEmpty()) {
            return MutateState.NOTHING;
        }

        ShoppingList list = getShoppingList(player);
        return list.receiveItem(itemStack, amount);
    }

    private void loadShoppingList(Player player) {
        Map<ItemStack, Integer> itemMap = new HashMap<>();
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        PersistentDataContainer shoppingList = pdc.get(KEY, PersistentDataType.TAG_CONTAINER);
        if (shoppingList != null) {
            List<PersistentDataContainer> items = shoppingList.get(KEY_DATA, PersistentDataType.LIST.dataContainers());
            for (PersistentDataContainer itemContainer : items) {
                int amount = itemContainer.get(new NamespacedKey(plugin, "amount"), PersistentDataType.INTEGER);
                String itemStackBase64 = itemContainer.get(new NamespacedKey(plugin, "itemstack"), PersistentDataType.STRING);

                if (itemStackBase64 != null) {
                    ItemStack itemStack = ItemStack.deserializeBytes(Base64.getDecoder().decode(itemStackBase64));
                    itemMap.put(itemStack, amount);
                }
            }
        }

        this.shoppingLists.put(player, new ShoppingList(itemMap));
    }

    private void saveShoppingList(Player player) {
        ShoppingList list = this.shoppingLists.get(player);
        if (list == null) {
            return;
        }

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        PersistentDataContainer shoppingListContainer = pdc.getAdapterContext().newPersistentDataContainer();
        List<PersistentDataContainer> itemContainers = new ArrayList<>();

        for (Map.Entry<ItemStack, Integer> entry : list.items().entrySet()) {
            int amount = entry.getValue();
            ItemStack itemStack = entry.getKey();

            PersistentDataContainer itemContainer = pdc.getAdapterContext().newPersistentDataContainer();
            itemContainer.set(new NamespacedKey(plugin, "amount"), PersistentDataType.INTEGER, amount);
            itemContainer.set(new NamespacedKey(plugin, "itemstack"), PersistentDataType.STRING,
                    Base64.getEncoder().encodeToString(itemStack.serializeAsBytes()));

            itemContainers.add(itemContainer);
        }

        shoppingListContainer.set(KEY_DATA, PersistentDataType.LIST.dataContainers(), itemContainers);
        pdc.set(KEY, PersistentDataType.TAG_CONTAINER, shoppingListContainer);
    }


    public enum MutateState {
        MODIFIED,
        REMOVED,
        NOTHING
    }
}
