package com.stifflered.bartercontainer.player;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.util.Messages;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Manages per-player shopping lists and persists them in the Player's PDC.

 * Storage model:
 *  - Player PDC root key "shopping_list" (KEY) holds a TAG_CONTAINER.
 *  - Inside it, KEY_DATA stores a LIST of TAG_CONTAINERs; each child container encodes:
 *      * amount: INTEGER
 *      * itemstack: STRING (base64 of ItemStack.serializeAsBytes())

 * Runtime model:
 *  - In-memory Map<Player, ShoppingList> cache, populated on join and saved on quit.
 *  - ShoppingList maps ItemStack → needed amount. (Equality relies on ItemStack.equals semantics.)

 * Notes:
 *  - The class is a Listener and self-registers in the constructor.
 *  - Exposes helper methods to add/remove/receive items and to fetch a player's list.

 * Internationalization:
 *  - Player-visible formatting moved to messages.yml:
 *      shopping.list.entry_suffix  (uses <amount> placeholder)
 */
public class ShoppingListManager implements Listener {

    /** Root key for the player's shopping list PDC. */
    private static final NamespacedKey KEY = new NamespacedKey(BarterContainer.INSTANCE, "shopping_list");
    /** Child key holding the list of item containers. */
    private static final NamespacedKey KEY_DATA = new NamespacedKey(BarterContainer.INSTANCE, "-data");

    /** Plugin reference (for NamespacedKey adapter context). */
    private final JavaPlugin plugin;
    /** In-memory cache of active players' shopping lists. */
    private final Map<Player, ShoppingList> shoppingLists = new HashMap<>();

    public ShoppingListManager(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Load a player's shopping list when they join (populate cache). */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        loadShoppingList(event.getPlayer());
    }

    /** Save and evict a player's shopping list when they quit (persist to PDC). */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        saveShoppingList(event.getPlayer());
        shoppingLists.remove(event.getPlayer());
    }

    /**
     * Immutable-ish wrapper for a player's shopping list.
     * Map<ItemStack, Integer> indicates required quantities per exact ItemStack (includes meta).
     */
    public record ShoppingList(Map<ItemStack, Integer> items) {

        /** Increment or insert an item requirement. */
        public void addItem(ItemStack itemStack, int amount) {
            this.items.put(itemStack, this.items.getOrDefault(itemStack, 0) + amount);
        }

        /** Remove an item requirement entirely. */
        public void removeItem(ItemStack material) {
            this.items.remove(material);
        }

        /**
         * Decrement needed amount for the given item by amountReceived.
         * When it reaches zero, the item is removed from the list.
         * @return MutateState indicating what happened.
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

        /**
         * Convert entries to display-ready components for book/GUI.

         * Previously appended a hard-coded " x<amount>" string. Now the suffix is pulled from messages.yml:
         *   shopping.list.entry_suffix
         * with placeholder:
         *   <amount> → integer amount remaining
         */
        public List<ShoppingListEntry> toDisplayList() {
            List<ShoppingListEntry> display = new ArrayList<>();
            for (Map.Entry<ItemStack, Integer> e : items.entrySet()) {
                Component name = Component.translatable(e.getKey()); // localized item name
                Component suffix = Messages.mm("shopping.list.entry_suffix", "amount", e.getValue());
                display.add(new ShoppingListEntry(name.append(suffix), e.getKey()));
            }

            return display;
        }

        /** Simple record for UI rendering: pre-styled text + reference ItemStack. */
        public record ShoppingListEntry(Component styled, ItemStack itemStack) {
        }
    }

    /** Access the in-memory list for a player (must have been loaded). */
    public ShoppingList getShoppingList(Player player) {
        return this.shoppingLists.get(player);
    }

    /** Add an item requirement to a player's list. */
    public void addItem(Player player, ItemStack material, int amount) {
        ShoppingList list = getShoppingList(player);
        list.addItem(material, amount);
    }

    /** Remove an item requirement from a player's list. */
    public void removeItem(Player player, ItemStack material) {
        ShoppingList list = getShoppingList(player);
        list.removeItem(material);
    }

    /** Receive the full stack amount towards the player's requirement for that stack. */
    public MutateState receive(Player player, ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isEmpty()) {
            return MutateState.NOTHING;
        }

        ShoppingList list = getShoppingList(player);
        return list.receiveItem(itemStack, itemStack.getAmount());
    }

    /** Receive an explicit amount towards the player's requirement for that stack. */
    public MutateState receive(Player player, ItemStack itemStack, int amount) {
        if (itemStack == null || itemStack.getType().isEmpty()) {
            return MutateState.NOTHING;
        }

        ShoppingList list = getShoppingList(player);
        return list.receiveItem(itemStack, amount);
    }

    /**
     * Deserialize a player's shopping list from their PDC into memory.
     * Format:
     *  - KEY -> TAG_CONTAINER
     *    - KEY_DATA -> LIST of TAG_CONTAINER
     *        - amount: INTEGER
     *        - itemstack: STRING (base64 bytes of ItemStack)

     * Defensive parsing:
     *  - Guard against null list (`items`) from PDC.
     *  - Guard against null boxed `Integer` for `amount`.
     *  - Skip invalid/empty payloads safely.
     */
    private void loadShoppingList(Player player) {
        Map<ItemStack, Integer> itemMap = new HashMap<>();
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        PersistentDataContainer shoppingList = pdc.get(KEY, PersistentDataType.TAG_CONTAINER);
        if (shoppingList != null) {
            List<PersistentDataContainer> items = shoppingList.get(KEY_DATA, PersistentDataType.LIST.dataContainers());
            if (items != null) {
                for (PersistentDataContainer itemContainer : items) {
                    if (itemContainer == null) continue;

                    Integer boxedAmount = itemContainer.get(new NamespacedKey(plugin, "amount"), PersistentDataType.INTEGER);
                    int amount = (boxedAmount != null) ? boxedAmount : 0;
                    if (amount <= 0) continue;

                    String itemStackBase64 = itemContainer.get(new NamespacedKey(plugin, "itemstack"), PersistentDataType.STRING);
                    if (itemStackBase64 == null || itemStackBase64.isEmpty()) continue;

                    try {
                        ItemStack itemStack = ItemStack.deserializeBytes(Base64.getDecoder().decode(itemStackBase64));
                        if (!itemStack.getType().isEmpty()) {
                            itemMap.put(itemStack, amount);
                        }
                    } catch (IllegalArgumentException | IllegalStateException ignored) {
                        // Corrupt or incompatible payload; skip gracefully
                    }
                }
            }
        }

        this.shoppingLists.put(player, new ShoppingList(itemMap));
    }


    /**
     * Serialize the in-memory list back into the player's PDC.
     * Uses a new TAG_CONTAINER with KEY_DATA set to a LIST of child TAG_CONTAINERs.
     */
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

    /** Mutation outcomes for receive operations. */
    public enum MutateState {
        MODIFIED,
        REMOVED,
        NOTHING
    }
}
