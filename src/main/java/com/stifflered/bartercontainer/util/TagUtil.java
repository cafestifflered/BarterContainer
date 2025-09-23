package com.stifflered.bartercontainer.util;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;

import org.jetbrains.annotations.Nullable;

/**
 * Convenience helpers for working with Bukkit Persistent Data Containers (PDC).

 * Highlights:
 *  - Typed get/set wrappers (String, int, double) plus generic methods.
 *  - NamespacedKey helpers: of("foo") → "barterchests:foo"; convertFromRawKey strips namespace.
 *  - has/remove utilities to simplify call sites.

 * Notes:
 *  - Methods accept any PersistentDataHolder (items, entities, blocks with TileState, players, etc.).
 *  - getTag(...) has an overload with a default value to avoid nullable handling.
 */
public class TagUtil {

    /** Remove a specific key from the holder's PDC. */
    public static void removeTag(PersistentDataHolder holder, NamespacedKey tag) {
        holder.getPersistentDataContainer().remove(tag);
    }

    /** Convenience: set a STRING tag. */
    public static void setStringTag(PersistentDataHolder holder, NamespacedKey tag, String tagValue) {
        setTag(holder, tag, PersistentDataType.STRING, tagValue);
    }

    /** Convenience: set an INTEGER tag. */
    public static void setIntegerTag(PersistentDataHolder holder, NamespacedKey tag, int tagValue) {
        setTag(holder, tag, PersistentDataType.INTEGER, tagValue);
    }

    /** Convenience: set a DOUBLE tag. */
    public static void setDoubleTag(PersistentDataHolder holder, NamespacedKey tag, double tagValue) {
        setTag(holder, tag, PersistentDataType.DOUBLE, tagValue);
    }

    /** Convenience: get a STRING tag (nullable). */
    @Nullable
    public static String getStringTag(PersistentDataHolder holder, NamespacedKey tag) {
        return getTag(holder, tag, PersistentDataType.STRING);
    }

    /** Convenience: get an INTEGER tag with default 0 if absent. */
    public static int getIntegerTag(PersistentDataHolder holder, NamespacedKey tag) {
        return getTag(holder, tag, PersistentDataType.INTEGER, 0);
    }

    /** Convenience: get a DOUBLE tag with default 0.0 if absent. */
    public static double getDoubleTag(PersistentDataHolder holder, NamespacedKey tag) {
        return getTag(holder, tag, PersistentDataType.DOUBLE, 0d);
    }

    /** Generic setter for any PersistentDataType. */
    public static <T, Z> void setTag(PersistentDataHolder holder, NamespacedKey tag, PersistentDataType<T, Z> type, Z value) {
        holder.getPersistentDataContainer().set(tag, type, value);
    }

    /** Generic getter (nullable) for any PersistentDataType. */
    public static <T, Z> Z getTag(PersistentDataHolder holder, NamespacedKey tag, PersistentDataType<T, Z> type) {
        return holder.getPersistentDataContainer().get(tag, type);
    }

    /** Generic getter with default value if absent. */
    public static <T, Z> Z getTag(PersistentDataHolder holder, NamespacedKey tag, PersistentDataType<T, Z> type, Z defaultValue) {
        return holder.getPersistentDataContainer().getOrDefault(tag, type, defaultValue);
    }

    /**
     * Strip the namespace from a namespaced key string.
     * Example: "minecraft:stone" → "stone"
     */
    public static String convertFromRawKey(String namespacedKey) {
        return namespacedKey.substring(namespacedKey.indexOf(':') + 1);
    }

    /**
     * Create a NamespacedKey under the plugin’s fixed namespace "barterchests".
     * Useful for non-runtime keys where you don't need the JavaPlugin instance.
     */
    public static NamespacedKey of(String tag) {
        return new NamespacedKey("barterchests", tag);
    }

    /** True if the holder has the given key in its PDC. */
    public static boolean hasTag(PersistentDataHolder holder, NamespacedKey key) {
        return holder.getPersistentDataContainer().has(key);
    }
}
