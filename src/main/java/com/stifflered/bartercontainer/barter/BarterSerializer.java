package com.stifflered.bartercontainer.barter;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.store.BarterStoreImpl;
import com.stifflered.bartercontainer.store.BarterStoreKey;
import com.stifflered.bartercontainer.util.TagUtil;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class BarterSerializer {

    private static final NamespacedKey BARTER_KEY = TagUtil.of("key");

    private static final NamespacedKey NAME_UUID = TagUtil.of("player_uuid");
    private static final NamespacedKey NAME_PLAYER = TagUtil.of("player_name");

    private static final NamespacedKey SHOP_ITEMS = TagUtil.of("shop_items");
    private static final NamespacedKey CURRENCY_STORAGE = TagUtil.of("currency_storage");
    private static final NamespacedKey PRICE_ITEM = TagUtil.of("price_item");

    @Nullable
    public void saveBarterStore(BarterStore barterStore, PersistentDataContainer container) {
        container.set(BARTER_KEY, PersistentDataType.STRING, barterStore.getKey().key().toString());

        PlayerProfile playerProfile = barterStore.getPlayerProfile();
        container.set(NAME_UUID, PersistentDataType.STRING, playerProfile.getId().toString());
        container.set(NAME_PLAYER, PersistentDataType.STRING, playerProfile.getName());

        this.storeItems(container, SHOP_ITEMS, Arrays.asList(barterStore.getSaleStorage().getContents()));
        this.storeItems(container, CURRENCY_STORAGE, Arrays.asList(barterStore.getCurrencyStorage().getContents()));

        container.set(PRICE_ITEM, PersistentDataType.STRING, Base64.getEncoder().encodeToString(barterStore.getCurrentItemPrice().serializeAsBytes()));
    }

    @Nullable
    public BarterStore getBarterStore(PersistentDataContainer container) {
        UUID uuid = UUID.fromString(container.get(BARTER_KEY, PersistentDataType.STRING));
        PlayerProfile playerProfile = Bukkit.createProfile(
                UUID.fromString(container.get(NAME_UUID, PersistentDataType.STRING)),
                container.get(NAME_PLAYER, PersistentDataType.STRING)
        );

        List<ItemStack> itemStacks = this.getItems(container, SHOP_ITEMS);
        List<ItemStack> currencyStorage = this.getItems(container, CURRENCY_STORAGE);
        ItemStack itemStack = ItemStack.deserializeBytes(Base64.getDecoder().decode(container.get(PRICE_ITEM, PersistentDataType.STRING)));

        return new BarterStoreImpl(new BarterStoreKeyImpl(uuid), playerProfile, itemStacks, currencyStorage, itemStack);
    }

    // Quick
    public boolean hasBarterStore(PersistentDataContainer persistentDataContainer) {
        return persistentDataContainer.has(BARTER_KEY);
    }


    public List<ItemStack> getItems(PersistentDataContainer container, NamespacedKey namespacedKey) {
        List<ItemStack> itemStacks = new ArrayList<>();
        if (!container.has(namespacedKey)) {
            return itemStacks;
        }

        for (String base64 : container.get(namespacedKey, PersistentDataType.STRING).split(";")) {
            if (!base64.isEmpty()) {
                itemStacks.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(base64)));
            }
        }

        return itemStacks;
    }


    public void storeItems(PersistentDataContainer container, NamespacedKey namespacedKey, List<ItemStack> itemStacks) {
        List<String> strings = new ArrayList<>();
        for (ItemStack itemStack : itemStacks) {
            if (itemStack != null) {
                strings.add(new String(Base64.getEncoder().encode(itemStack.serializeAsBytes())));
            }
        }

        container.set(namespacedKey, PersistentDataType.STRING, String.join(";", strings));
    }


    public record BarterStoreKeyImpl(UUID uuid) implements BarterStoreKey {

        @Override
        public UUID key() {
            return this.uuid;
        }
    }
}
