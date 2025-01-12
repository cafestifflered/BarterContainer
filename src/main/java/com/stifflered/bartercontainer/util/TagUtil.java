package com.stifflered.bartercontainer.util;

import com.stifflered.bartercontainer.BarterContainer;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

public class TagUtil {

    public static void removeTag(PersistentDataHolder holder, NamespacedKey tag) {
        holder.getPersistentDataContainer().remove(tag);
    }

    public static void setStringTag(PersistentDataHolder holder, NamespacedKey tag, String tagValue) {
        setTag(holder, tag, PersistentDataType.STRING, tagValue);
    }

    public static void setIntegerTag(PersistentDataHolder holder, NamespacedKey tag, int tagValue) {
        setTag(holder, tag, PersistentDataType.INTEGER, tagValue);
    }

    public static void setDoubleTag(PersistentDataHolder holder, NamespacedKey tag, double tagValue) {
        setTag(holder, tag, PersistentDataType.DOUBLE, tagValue);
    }

    @Nullable
    public static String getStringTag(PersistentDataHolder holder, NamespacedKey tag) {
        return getTag(holder, tag, PersistentDataType.STRING);
    }

    public static int getIntegerTag(PersistentDataHolder holder, NamespacedKey tag) {
        return getTag(holder, tag, PersistentDataType.INTEGER, 0);
    }

    public static double getDoubleTag(PersistentDataHolder holder, NamespacedKey tag) {
        return getTag(holder, tag, PersistentDataType.DOUBLE, 0d);
    }

    public static <T, Z> void setTag(PersistentDataHolder holder, NamespacedKey tag, PersistentDataType<T, Z> type, Z value) {
        holder.getPersistentDataContainer().set(tag, type, value);
    }

    public static <T, Z> Z getTag(PersistentDataHolder holder, NamespacedKey tag, PersistentDataType<T, Z> type) {
        return holder.getPersistentDataContainer().get(tag, type);
    }

    public static <T, Z> Z getTag(PersistentDataHolder holder, NamespacedKey tag, PersistentDataType<T, Z> type, Z defaultValue) {
        return holder.getPersistentDataContainer().getOrDefault(tag, type, defaultValue);
    }

    public static String convertFromRawKey(String namespacedKey) {
        return namespacedKey.substring(namespacedKey.indexOf(':') + 1);
    }

    public static NamespacedKey of(String tag) {
        return new NamespacedKey("bartercontainer", tag);
    }

    public static boolean hasTag(PersistentDataHolder holder, NamespacedKey key) {
        return holder.getPersistentDataContainer().has(key);
    }
}
