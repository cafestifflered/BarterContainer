package com.stifflered.bartercontainer.barter.serializers;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.stifflered.bartercontainer.barter.BarterStoreKeyImpl;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.store.BarterStoreImpl;
import com.stifflered.bartercontainer.store.BarterStoreKey;
import com.stifflered.bartercontainer.util.source.codec.*;

import org.bukkit.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * FileBarterSerializer

 * Responsible for converting a {@link BarterStore} to/from a JSON representation
 * suitable for persistent storage on disk (see util.source.impl.BarterStorage).

 * Notes on format:
 *  - key:              UUID string of the store key
 *  - player_uuid:      UUID string of the owner (PlayerProfile#getId)
 *  - player_name:      Last known player name (for display; UUID is canonical)
 *  - sale_storage:     JSON array of Base64-encoded ItemStacks (one per slot)
 *  - currency_storage: JSON array of Base64-encoded ItemStacks (one per slot)
 *  - price_item:       Base64-encoded ItemStack (the current price item)
 *  - locations:        JSON array of {x,y,z,world} objects (block coords only)

 * Item serialization:
 *  - Uses Bukkit ItemStack#serializeAsBytes + Base64 to produce strings.
 *  - Empty/missing slots are saved as "" and read back as AIR ItemStacks.

 * Location serialization:
 *  - Uses {@link BlockLocationSerializer} to encode block coords + world key.
 *  - Null locations are skipped on save.
 */
public class FileBarterSerializer {

    /**
     * Write a BarterStore into the provided JSON object.
     * The caller is responsible for writing this object to disk.
     */
    public void saveBarterStore(BarterStore barterStore, JsonObject container) {
        container.addProperty("key", barterStore.getKey().key().toString());

        PlayerProfile playerProfile = barterStore.getPlayerProfile();
        container.addProperty("player_uuid", Objects.requireNonNull(playerProfile.getId()).toString());
        container.addProperty("player_name", playerProfile.getName());

        this.storeItems(container, "sale_storage", barterStore.getSaleStorage());
        this.storeItems(container, "currency_storage", barterStore.getCurrencyStorage());

        // Current price item serialized as Base64 (single stack)
        container.addProperty("price_item", Base64.getEncoder().encodeToString(barterStore.getCurrentItemPrice().serializeAsBytes()));
        this.storeLocations(container, barterStore.getLocations());
    }

    /**
     * Rebuild a BarterStore from its JSON representation.
     * Creates a {@link BarterStoreImpl} with:
     *  - store key
     *  - owner profile (UUID + last known name)
     *  - sale + currency inventories (lists of ItemStacks)
     *  - price item
     *  - bound locations
     */
    public BarterStore getBarterStore(JsonObject container) {
        BarterStoreKey barterStoreKey = new BarterStoreKeyImpl(UUID.fromString(container.get("key").getAsString()));
        PlayerProfile playerProfile = Bukkit.createProfile(
                UUID.fromString(container.get("player_uuid").getAsString()),
                container.get("player_name").getAsString()
        );

        List<ItemStack> saleItems = this.getItems(container,"sale_storage");
        List<ItemStack> currencyStorage = this.getItems(container,"currency_storage");

        ItemStack priceItem = ItemStack.deserializeBytes(Base64.getDecoder().decode(container.get("price_item").getAsString()));
        List<Location> locations = this.getLocations(container);

        return new BarterStoreImpl(barterStoreKey, playerProfile, saleItems, currencyStorage, priceItem, locations);
    }

    /**
     * Serialize an inventory into a JSON array of Base64 strings.
     * - Non-null items → Base64(ItemStack#serializeAsBytes)
     * - Null slots     → empty string ""
     */
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

    /**
     * Deserialize a list of ItemStacks from a JSON array of Base64 strings.
     * - "" becomes an AIR ItemStack placeholder to preserve slot alignment.
     * - Missing arrays produce an empty list.
     */
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

    /**
     * Serialize locations to a JSON array using block coordinates and world key.
     * Null entries are ignored.
     */
    private void storeLocations(JsonObject main, List<Location> locs) {
        JsonArray array = new JsonArray();
        main.add("locations", array);
        for (Location location :  locs) {
            if (location != null) {
                array.add(BlockLocationSerializer.INSTANCE.encode(location));
            }
        }
    }

    /**
     * Deserialize a list of Locations from the JSON array.
     * - Any decode failures are caught and printed, and that entry is skipped.
     * - Missing arrays produce an empty list.
     */
    private List<Location> getLocations(JsonObject container) {
        List<Location> locations = new ArrayList<>();
        if (!container.has("locations")) {
            return locations;
        }

        for (JsonElement obj : container.getAsJsonArray("locations")) {
            try {
                locations.add(BlockLocationSerializer.INSTANCE.decode(obj.getAsJsonObject()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return locations;
    }
}
