package com.stifflered.bartercontainer.store;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.stifflered.bartercontainer.barter.permission.BarterRole;
import com.stifflered.bartercontainer.store.owners.BankOwner;
import com.stifflered.bartercontainer.util.Components;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class BarterStoreImpl implements BarterStore {

    private final BarterStoreKey barterStoreKey;
    private final PlayerProfile playerProfile;
    private Inventory itemStacks;
    private Inventory currencyHolder;
    private ItemStack itemStack;

    public BarterStoreImpl(BarterStoreKey barterStoreKey, PlayerProfile playerProfile, List<ItemStack> itemStacks, List<ItemStack> currencyItems, ItemStack itemStack) {
        this.barterStoreKey = barterStoreKey;
        this.playerProfile = playerProfile;
        this.itemStacks = Bukkit.createInventory(null, 27);
        for (ItemStack itemToAdd : itemStacks) {
            this.itemStacks.addItem(itemToAdd);
        }
        this.currencyHolder = Bukkit.createInventory(new BankOwner(this), 27);
        for (ItemStack itemToAdd : currencyItems) {
            this.currencyHolder.addItem(itemToAdd);
        }
        this.itemStack = itemStack;
    }


    @Override
    public PlayerProfile getPlayerProfile() {
        return this.playerProfile;
    }

    @Override
    public BarterStoreKey getKey() {
        return this.barterStoreKey;
    }

    @Override
    public Inventory getSaleStorage() {
        return this.itemStacks;
    }

    @Override
    public Inventory getCurrencyStorage() {
        return this.currencyHolder;
    }


    @Override
    public boolean canBreak(Player player) {
        boolean upkeeper = this.getRole(player) == BarterRole.UPKEEPER;
        if (upkeeper) {
            boolean isEmpty = this.currencyHolder.isEmpty() && this.itemStacks.isEmpty();
            if (isEmpty) {
                return true;
            } else {
                player.sendMessage(Components.prefixedError(Component.text("You must empty both the Barter Bank and Shop Items.")));
                return false;
            }
        }

        return upkeeper;
    }

    @Override
    public Component getNameStyled() {
        return Component.text(this.playerProfile.getName() + "'s Barter Container");
    }

    @Override
    public ItemStack getCurrentItemPrice() {
        return this.itemStack.clone();
    }

    @Override
    public void setCurrentItemPrice(ItemStack itemStack) {
        this.itemStack = itemStack.clone();
    }

    @Override
    public BarterRole getRole(Player player) {
        if (player.getUniqueId().equals(this.playerProfile.getId())) {
            return BarterRole.UPKEEPER;
        }

        return BarterRole.VISITOR;
    }
}
