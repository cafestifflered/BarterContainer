package com.stifflered.bartercontainer.store;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.stifflered.bartercontainer.barter.permission.BarterRole;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Lets store in the bartering object:
 * - owner (player profile)
 * - time created
 * - bartering item
 */
public interface BarterStore {

    PlayerProfile getPlayerProfile();

    BarterStoreKey getKey();

    Inventory getSaleStorage();

    Inventory getCurrencyStorage();


    boolean canBreak(Player player);

    Component getNameStyled();

    ItemStack getCurrentItemPrice();

    void setCurrentItemPrice(ItemStack itemStack);

    BarterRole getRole(Player player);
}
