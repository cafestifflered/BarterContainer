package com.stifflered.bartercontainer.command;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.gui.common.SimplePaginator;
import com.stifflered.bartercontainer.item.ItemInstances;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CatalogueCommand extends BukkitCommand {

    public CatalogueCommand() {
        super("catalog");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        Player player = (Player) sender;
        if (!sender.hasPermission("barterchests.catalog")) {
            sender.sendMessage(Components.NO_PERMISSION);
            return true;
        }


        ChestGui loading = new ChestGui(6, ComponentHolder.of(Component.text("Loading...")));
        loading.setOnGlobalClick((event) -> {
            event.setCancelled(true);
        });
        loading.show(player);

        new BukkitRunnable() {

            @Override
            public void run() {
                Map<ItemStack, List<BarterStore>> sellRegistry = new HashMap<>();
                List<BarterStore> containers = BarterManager.INSTANCE.getAll();
                for (BarterStore store : containers) {
                    for (ItemStack itemStack : store.getSaleStorage().getContents()) {
                        if (itemStack != null && !itemStack.isEmpty()) {
                            ItemStack checkItem = itemStack.clone();
                            checkItem.setAmount(1);
                            List<BarterStore> oldStores = sellRegistry.computeIfAbsent(checkItem, k -> new ArrayList<>());

                            boolean shouldRegister = true;
                            for (BarterStore oldStore : oldStores) {
                                // Dont allow the same store to be registered multiple times
                                if (oldStore.getKey().equals(store.getKey())) {
                                    shouldRegister = false;
                                    break;
                                }
                                // Dont allow the same player to sell for the same item
                                if (oldStore.getCurrentItemPrice().equals(store.getCurrentItemPrice()) && oldStore.getPlayerProfile().getId().equals(store.getPlayerProfile().getId())) {
                                    shouldRegister = false;
                                    break;
                                }
                            }

                            if (shouldRegister) {
                                oldStores.add(store);
                            }
                        }
                    }
                }


                List<GuiItem> items = new ArrayList<>();
                for (Map.Entry<ItemStack, List<BarterStore>> entry : sellRegistry.entrySet()) {
                    ItemStack baseItem = entry.getKey().clone();
                    ItemUtil.wrapEdit(baseItem, (meta) -> {
                        List<Component> lore = new ArrayList<>(entry.getValue().size());
                        lore.add(Component.text("Sold By:", NamedTextColor.GREEN));
                        lore.add(Component.empty());
                        for (BarterStore store : entry.getValue()) {
                            lore.add(Component.text(store.getPlayerProfile().getName() + " - " + store.getCurrentItemPrice().getType().name(), NamedTextColor.GREEN));
                        }

                        Components.lore(meta, lore);
                    });
                    items.add(new GuiItem(baseItem));
                }
                new BukkitRunnable() {

                    @Override
                    public void run() {

                        new SimplePaginator(6, ComponentHolder.of(Component.text("Catalog")), items , null).show(player);
                    }
                }.runTask(BarterContainer.INSTANCE);
            }
        }.runTaskAsynchronously(BarterContainer.INSTANCE);
        return true;
    }
}
