package com.stifflered.tcchallenge.item;

import com.stifflered.tcchallenge.BarterChallenge;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

public abstract class ItemInstance implements Listener {

    public static final Map<String, ItemInstance> INSTANCE_STORAGE = new HashMap<>();

    public static NamespacedKey KEY = new NamespacedKey(BarterChallenge.INSTANCE, "item_instance");
    protected final ItemStack stack;
    private final String key;

    public ItemInstance(String key, ItemStack stack) {
        this.key = key;
        this.stack = this.applyItemStack(stack);
    }

    public static ItemInstance fromStack(ItemStack stack) {
        if (stack == null) {
            return null;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (!container.has(KEY)) {
            return null;
        }

        return INSTANCE_STORAGE.get(container.get(KEY, PersistentDataType.STRING));
    }

    protected ItemStack applyItemStack(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        INSTANCE_STORAGE.put(this.key, this);
        stack.editMeta((meta) -> {
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, this.key);
        });
        return stack;
    }

    public void interact(PlayerInteractEvent event) {
    }

    public void place(BlockPlaceEvent event) {
    }

    public ItemStack getItem() {
        return this.stack;
    }

}
