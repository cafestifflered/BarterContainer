package com.stifflered.bartercontainer.gui.catalogue;

import com.github.stefvanschie.inventoryframework.adventuresupport.*;
import com.github.stefvanschie.inventoryframework.gui.*;
import com.github.stefvanschie.inventoryframework.gui.type.*;
import com.google.common.collect.Comparators;
import com.stifflered.bartercontainer.*;
import com.stifflered.bartercontainer.barter.*;
import com.stifflered.bartercontainer.gui.common.*;
import com.stifflered.bartercontainer.store.*;
import com.stifflered.bartercontainer.util.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.*;
import org.jetbrains.annotations.*;

import java.util.*;

public class CatalogueGui extends ChestGui {

    public CatalogueGui() {
        super(6, ComponentHolder.of(BarterContainer.INSTANCE.getConfiguration().getCatalogueTitle()));
        this.setOnGlobalClick((event) -> {
            event.setCancelled(true);
        });
    }

    @Override
    public void show(@NotNull HumanEntity humanEntity) {
        super.show(humanEntity);
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
//                                // Dont allow the same player to sell for the same item
//                                if (oldStore.getCurrentItemPrice().equals(store.getCurrentItemPrice()) && oldStore.getPlayerProfile().getId().equals(store.getPlayerProfile().getId())) {
//                                    shouldRegister = false;
//                                    break;
//                                }
                            }

                            if (shouldRegister) {
                                oldStores.add(store);
                            }
                        }
                    }
                }


                List<GuiItem> items = new ArrayList<>();
                for (Map.Entry<ItemStack, List<BarterStore>> entry : sellRegistry.entrySet()) {
                    items.add(formatItem(entry.getKey().clone(), entry.getValue()));
                }
                items.sort(Comparator.comparing(o -> o.getItem().getType().key())); // Sort by name of item

                new BukkitRunnable() {

                    @Override
                    public void run() {
                        new SimplePaginator(6, ComponentHolder.of(BarterContainer.INSTANCE.getConfiguration().getCatalogueTitle()), items, null).show(humanEntity);
                    }
                }.runTask(BarterContainer.INSTANCE);
            }
        }.runTaskAsynchronously(BarterContainer.INSTANCE);
    }

    protected GuiItem formatItem(ItemStack baseItem, List<BarterStore> items) {
        ItemUtil.wrapEdit(baseItem, (meta) -> {
            List<Component> lore = new ArrayList<>(items.size());
            lore.addAll(BarterContainer.INSTANCE.getConfiguration().getCatalogueItemLore());
            lore.add(Component.empty());
            for (BarterStore store : items) {
                if (!store.getLocations().isEmpty()) {
                    Location location = store.getLocations().getFirst();
                    String text = location.getBlockX() + " "  + location.getBlockY() + " " + location.getBlockZ();
                    lore.add(Component.text("~ " + text, NamedTextColor.GREEN));
                }
            }

            Components.lore(meta, lore);
        });
        return new GuiItem(baseItem);
    }

    private static List<GuiItem> getListedItems() {
        List<GuiItem> items = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.isItem()) {
                items.add(new GuiItem(new ItemStack(material)));
            }
        }

        return items;
    }

}
