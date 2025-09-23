package com.stifflered.bartercontainer.store;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.stifflered.bartercontainer.barter.permission.BarterRole;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Domain interface for a "barter store" – a player-owned shop container.

 * A BarterStore encapsulates all the persistent and runtime state of a single shop:
 *   - Who owns it (player profile).
 *   - Its unique key for storage/caching.
 *   - The backing inventories (sale items and currency bank).
 *   - Current barter price (as an ItemStack, flexible beyond vanilla currencies).
 *   - Role-based access control (BarterRole).
 *   - Physical world locations (e.g., blocks that represent/compose this shop).

 * Implementations (e.g., {@link com.stifflered.bartercontainer.store.BarterStoreImpl}) handle
 * persistence, validation, and concrete inventory behavior.

 * Typical lifecycle:
 *   1. Created via BarterManager#createNewStore, written into block PDC.
 *   2. Cached in BarterManager#storage while chunk is loaded.
 *   3. Saved/unloaded on chunk unload or plugin shutdown.
 */
public interface BarterStore {

    /** Owner of the store, captured as a Paper PlayerProfile (supports offline profiles, not just live Player). */
    PlayerProfile getPlayerProfile();

    /** Unique identity for this store, usually a UUID wrapped in a BarterStoreKey. */
    BarterStoreKey getKey();

    /** Inventory holding the items the shop is selling. */
    Inventory getSaleStorage();

    /** Inventory holding the "currency" items received from barters. */
    Inventory getCurrencyStorage();

    /**
     * Determines if the given player has permission to break/destroy this shop.
     * Typically, checks ownership or roles.
     */
    boolean canBreak(Player player);

    /** Styled display name for the shop (adventure Component, often MiniMessage-based). */
    Component getNameStyled();

    /** The current asking price for a single unit of trade (represented as an ItemStack, flexible currency model). */
    ItemStack getCurrentItemPrice();

    /** Update the current price ItemStack. */
    void setCurrentItemPrice(ItemStack itemStack);

    /**
     * Returns the role of a given player with respect to this store.
     * Supports role-based permissions (owner, co-owner, visitor, etc.).
     */
    BarterRole getRole(Player player);

    /**
     * World locations that represent this store (e.g., barrels, signs, etc.).
     * A store may span multiple block locations depending on design.
     */
    List<Location> getLocations();

}
