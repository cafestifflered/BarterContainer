package com.stifflered.bartercontainer.store;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.stifflered.bartercontainer.barter.permission.BarterPermission;
import com.stifflered.bartercontainer.barter.permission.BarterRole;
import com.stifflered.bartercontainer.store.owners.BankOwner;
import com.stifflered.bartercontainer.util.Components;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
    private Location location;

    public BarterStoreImpl(BarterStoreKey barterStoreKey, PlayerProfile playerProfile, List<ItemStack> itemStacks, List<ItemStack> currencyItems, ItemStack itemStack) {
        this.barterStoreKey = barterStoreKey;
        this.playerProfile = playerProfile;
        this.itemStacks = Bukkit.createInventory(null, 27);
        this.itemStacks.setContents(itemStacks.toArray(new ItemStack[0]));
        this.currencyHolder = Bukkit.createInventory(new BankOwner(this), 27);
        this.currencyHolder.setContents(currencyItems.toArray(new ItemStack[0]));
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
        boolean canDelete = this.getRole(player).hasPermission(BarterPermission.DELETE);
        if (canDelete) {
            boolean isEmpty = this.currencyHolder.isEmpty() && this.itemStacks.isEmpty();
            if (isEmpty) {
                return true;
            } else {
                player.sendMessage(Components.prefixedError(Component.text("You must empty both the Barter Bank and Shop Items.")));
                return false;
            }
        }

        return canDelete;
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
        if (player.hasPermission("barterchests.admin")) {
            return BarterRole.UPKEEPER;
        }

        if (player.getUniqueId().equals(this.playerProfile.getId())) {
            return BarterRole.UPKEEPER;
        }

        return BarterRole.VISITOR;
    }

    @Override
    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }
}
