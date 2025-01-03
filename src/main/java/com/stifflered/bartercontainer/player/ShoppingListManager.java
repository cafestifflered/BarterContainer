package com.stifflered.bartercontainer.player;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.util.BarterShopOwnerLogManager;
import com.stifflered.bartercontainer.util.ListPaginator;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
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

    public void registerCommands(BarterContainer barterContainer) {
        barterContainer.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS.newHandler(event -> {
            event.registrar().register(
                    barterContainer.getPluginMeta(),
                    Commands.literal("shopping-list").then(
                                    Commands.literal("add").then(
                                            Commands.argument("item", ArgumentTypes.resource(RegistryKey.ITEM)).then(
                                                    Commands.argument("amount", IntegerArgumentType.integer(0))
                                                            .executes(context -> {
                                                                ItemType itemType = context.getArgument("item", ItemType.class);
                                                                int amount = IntegerArgumentType.getInteger(context, "amount");
                                                                if (context.getSource().getSender() instanceof Player player) {
                                                                    player.sendMessage(Component.text("Added " + itemType.key() + " to your shopping list!", NamedTextColor.GREEN));
                                                                    addItem(player, itemType.asMaterial(), amount);
                                                                }

                                                                return Command.SINGLE_SUCCESS;
                                                            })
                                            )
                                    )
                            )
                            .then(Commands.literal("remove").then(
                                            Commands.argument("item", ArgumentTypes.resource(RegistryKey.ITEM)).executes(context -> {
                                                ItemType itemType = context.getArgument("item", ItemType.class);
                                                if (context.getSource().getSender() instanceof Player player) {
                                                    player.sendMessage(Component.text("Removed " + itemType.key() + " from your shopping list!", NamedTextColor.GREEN));
                                                    removeItem(player, itemType.asMaterial());
                                                }

                                                return Command.SINGLE_SUCCESS;
                                            })
                                    )
                            )
                            .executes(context -> {
                                if (context.getSource().getSender() instanceof Player player) {
                                    ListPaginator<ShoppingList.ShoppingListEntry> entryListPaginator = new ListPaginator<>(getShoppingList(player).toDisplayList(), 13);
                                    List<Component> pages = new ArrayList<>();
                                    for (int i = 0; i < entryListPaginator.getTotalPages(); i++) {
                                        TextComponent.Builder builder = Component.text();
                                        for (ShoppingList.ShoppingListEntry record : entryListPaginator.getPage(i)) {
                                            builder.append(record.styled.append(Component.text(" [X]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/shopping-list remove " + record.material.key())))).appendNewline();
                                        }
                                        pages.add(builder.build());
                                    }
                                    player.openBook(Book.book(Component.text("Data"), Component.text("?"), pages));
                                }
                                return Command.SINGLE_SUCCESS;
                            })
                            .build(),
                    "Add items",
                    List.of()
            );
        }));
    }


    public record ShoppingList(Map<Material, Integer> items) {

        public void addItem(Material material, int amount) {
            this.items.put(material, amount);
        }

        public void removeItem(Material material) {
            this.items.remove(material);
        }

        /**
         * Decrement needed amount for the given material by 'amountReceived'.
         * Clamps to zero (does not remove).
         */
        public MutateState receiveItem(Material material, int amountReceived) {
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
            for (Map.Entry<Material, Integer> e : items.entrySet()) {
                display.add(new ShoppingListEntry(Component.translatable(e.getKey()).append(Component.text(" x" + e.getValue())), e.getKey()));
            }


            return display;
        }

        public record ShoppingListEntry(Component styled, Material material) {

        }
    }

    public ShoppingList getShoppingList(Player player) {
        return this.shoppingLists.get(player);
    }

    public void addItem(Player player, Material material, int amount) {
        ShoppingList list = getShoppingList(player);
        list.addItem(material, amount);
    }

    public void removeItem(Player player, Material material) {
        ShoppingList list = getShoppingList(player);
        list.removeItem(material);
    }

    public MutateState receive(Player player, ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isEmpty()) {
            return MutateState.NOTHING;
        }

        Material material = itemStack.getType();
        ShoppingList list = getShoppingList(player);
        return list.receiveItem(material, itemStack.getAmount());
    }

    private void loadShoppingList(Player player) {
        Map<Material, Integer> itemMap = new HashMap<>();
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        PersistentDataContainer shoppingList = pdc.get(KEY, PersistentDataType.TAG_CONTAINER);
        if (shoppingList != null) {
            PersistentDataContainer items = shoppingList.get(KEY_DATA, PersistentDataType.TAG_CONTAINER);
            for (NamespacedKey subKey : items.getKeys()) {
                Integer amount = items.get(subKey, PersistentDataType.INTEGER);
                Material material = Material.matchMaterial(subKey.getKey());
                if (material != null) {
                    itemMap.put(material, amount);
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
        PersistentDataContainer itemsContainer = pdc.getAdapterContext().newPersistentDataContainer();

        for (Map.Entry<Material, Integer> entry : list.items().entrySet()) {
            Material material = entry.getKey();
            int amount = entry.getValue();
            NamespacedKey materialKey = new NamespacedKey(this.plugin, material.name().toLowerCase(Locale.ROOT));
            itemsContainer.set(materialKey, PersistentDataType.INTEGER, amount);
        }

        shoppingListContainer.set(KEY_DATA, PersistentDataType.TAG_CONTAINER, itemsContainer);
        pdc.set(KEY, PersistentDataType.TAG_CONTAINER, shoppingListContainer);
    }

    public enum MutateState {
        MODIFIED,
        REMOVED,
        NOTHING
    }
}
