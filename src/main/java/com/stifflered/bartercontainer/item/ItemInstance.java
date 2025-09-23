package com.stifflered.bartercontainer.item;

import com.stifflered.bartercontainer.BarterContainer;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for custom logical "item instances" that behave like identifiable tools/tokens.

 * Core idea:
 *  - Each concrete ItemInstance wraps an ItemStack and stamps it with a unique string ID in PDC (KEY).
 *  - STATIC registry (INSTANCE_STORAGE) maps that ID → the ItemInstance object.
 *  - fromStack(...) can recover the ItemInstance for any given ItemStack by reading its PDC key.

 * Event integration:
 *  - Implements Listener so subclasses can subscribe to Bukkit events (e.g., interact/place).
 *  - Provides empty hooks interact(...) and place(...) for subclasses to override.

 * Lifecycle notes:
 *  - applyItemStack(...) populates INSTANCE_STORAGE and writes the PDC tag when the subclass is constructed.
 *  - Because instances are singletons (see ItemInstances), the registry provides global lookup.

 * Caveats:
 *  - INSTANCE_STORAGE is process-wide and non-weak; if many transient instances were created, it could grow.
 *    In current design, items are singletons (safe).
 */
public abstract class ItemInstance implements Listener {

    /** Global registry of known item instances, keyed by their string identifier written into PDC.

     *  Switched to ConcurrentHashMap to be safe if instance registration/lookup
     *  is ever performed from async tasks. No behavioral change on main-thread usage.
     */
    public static final Map<String, ItemInstance> INSTANCE_STORAGE = new ConcurrentHashMap<>();

    /** PDC key used to store the instance identifier on item meta.
     *  Marked final to prevent accidental reassignment at runtime.
     *  This is a stable namespaced key used by all ItemInstance stacks.
     */
    public static final NamespacedKey KEY = new NamespacedKey(BarterContainer.INSTANCE, "item_instance");

    /** The physical ItemStack representing this logical instance (already tagged with KEY). */
    protected final ItemStack stack;

    /** The unique identifier stored in PDC to resolve back to this instance. */
    private final String key;

    /**
     * @param key   unique identifier to store in PDC
     * @param stack base ItemStack that will be tagged and exposed via getItem()

     * Behavior:
     *  - Calls applyItemStack to stamp the PDC and register this instance globally.
     */
    public ItemInstance(String key, ItemStack stack) {
        this.key = key;
        this.stack = this.applyItemStack(stack);
    }

    /**
     * Resolve an ItemInstance from a tagged ItemStack.
     * @return the registered ItemInstance or null if stack/meta/PDC missing or unknown key.

     * Defensive improvements:
     *  - Null-guard on PDC read: even when has(KEY) is true, we avoid NPEs if a plugin clobbers the value.
     */
    public static ItemInstance fromStack(ItemStack stack) {
        if (stack == null) {
            return null;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (!container.has(KEY, PersistentDataType.STRING)) {
            return null;
        }

        String id = container.get(KEY, PersistentDataType.STRING);
        return (id == null) ? null : INSTANCE_STORAGE.get(id);
    }


    /**
     * Stamps the ItemStack with this instance's key and registers it globally.
     * Returns the same stack (for chaining/assignment).

     * Null-safety:
     *  - If provided stack is null, returns null (caller should avoid constructing with null).
     */
    protected ItemStack applyItemStack(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        INSTANCE_STORAGE.put(this.key, this);
        stack.editMeta((meta) -> meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, this.key));
        return stack;
    }

    /** Hook for right-click/left-click interactions (subclasses override as needed). */
    public void interact(PlayerInteractEvent event) {
    }

    /** Hook for block placement using this item (subclasses override as needed). */
    public void place(BlockPlaceEvent event) {
    }

    /** Returns the tagged ItemStack for giving to players or comparing in inventories. */
    public ItemStack getItem() {
        return this.stack;
    }

}
