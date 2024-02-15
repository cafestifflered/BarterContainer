package com.stifflered.bartercontainer.barter.serializers;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.stifflered.bartercontainer.barter.BarterStoreKeyImpl;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.store.BarterStoreImpl;
import com.stifflered.bartercontainer.store.BarterStoreKey;
import com.stifflered.bartercontainer.util.TagUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class InGameBarterSerializer {

    private static final NamespacedKey BARTER_KEY_TAG = TagUtil.of("barter_key");

    public Optional<BarterStoreKey> getBarterStoreKey(PersistentDataContainer container) {
        return Optional.ofNullable(container.get(BARTER_KEY_TAG, PersistentDataType.STRING))
                .map(UUID::fromString)
                .map(BarterStoreKeyImpl::new);
    }

    public void writeBarterStoreKey(PersistentDataContainer container, BarterStore barterStore) {
        container.set(BARTER_KEY_TAG, PersistentDataType.STRING, barterStore.getKey().key().toString());
    }


}
