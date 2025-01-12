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
import com.stifflered.bartercontainer.util.source.codec.*;
import org.bukkit.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FileBarterSerializer {

    public void saveBarterStore(BarterStore barterStore, JsonObject container) {
        container.addProperty("key", barterStore.getKey().key().toString());

        PlayerProfile playerProfile = barterStore.getPlayerProfile();
        container.addProperty("player_uuid", playerProfile.getId().toString());
        container.addProperty("player_name", playerProfile.getName());

        this.storeItems(container, "sale_storage", barterStore.getSaleStorage());
        this.storeItems(container, "currency_storage", barterStore.getCurrencyStorage());

        container.addProperty("price_item", Base64.getEncoder().encodeToString(barterStore.getCurrentItemPrice().serializeAsBytes()));
        this.storeLocations(container, "locations", barterStore.getLocations());
    }

    public BarterStore getBarterStore(JsonObject container) {
        BarterStoreKey barterStoreKey = new BarterStoreKeyImpl(UUID.fromString(container.get("key").getAsString()));
        PlayerProfile playerProfile = Bukkit.createProfile(
                UUID.fromString(container.get("player_uuid").getAsString()),
                container.get("player_name").getAsString()
        );

        List<ItemStack> saleItems = this.getItems(container,"sale_storage");
        List<ItemStack> currencyStorage = this.getItems(container,"currency_storage");

        ItemStack priceItem = ItemStack.deserializeBytes(Base64.getDecoder().decode(container.get("price_item").getAsString()));
        List<Location> locations = this.getLocations(container,"locations");

        return new BarterStoreImpl(barterStoreKey, playerProfile, saleItems, currencyStorage, priceItem, locations);
    }

    private void storeItems(JsonObject main, String name, Inventory inventory) {
        JsonArray array = new JsonArray();
        main.add(name, array);
        for (ItemStack itemStack : inventory.getContents()) {
            if (itemStack != null) {
                array.add(new String(Base64.getEncoder().encode(itemStack.serializeAsBytes())));
            } else {
                array.add("");
            }
        }
    }

    private List<ItemStack> getItems(JsonObject container, String name) {
        List<ItemStack> itemStacks = new ArrayList<>();
        if (!container.has(name)) {
            return itemStacks;
        }

        for (JsonElement slotNum : container.getAsJsonArray(name)) {
            String value = slotNum.getAsString();
            if (!value.isEmpty()) {
                itemStacks.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(value)));
            } else {
                itemStacks.add(new ItemStack(Material.AIR));
            }
        }

        return itemStacks;
    }

    private void storeLocations(JsonObject main, String name, List<Location> locs) {
        JsonArray array = new JsonArray();
        main.add(name, array);
        for (Location location :  locs) {
            if (location != null) {
                array.add(BlockLocationSerializer.INSTANCE.encode(location));
            }
        }
    }

    private List<Location> getLocations(JsonObject container, String name) {
        List<Location> locations = new ArrayList<>();
        if (!container.has(name)) {
            return locations;
        }

        for (JsonElement obj : container.getAsJsonArray(name)) {
            try {
                locations.add(BlockLocationSerializer.INSTANCE.decode(obj.getAsJsonObject()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return locations;
    }
}
