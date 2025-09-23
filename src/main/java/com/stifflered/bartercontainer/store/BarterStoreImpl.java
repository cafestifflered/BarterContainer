package com.stifflered.bartercontainer.store;

import com.destroystokyo.paper.profile.PlayerProfile;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.barter.permission.BarterPermission;
import com.stifflered.bartercontainer.barter.permission.BarterRole;
import com.stifflered.bartercontainer.store.owners.BankOwner;
import com.stifflered.bartercontainer.store.owners.SaveOnClose;
import com.stifflered.bartercontainer.util.Messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Concrete implementation of a player-owned barter shop.

 * Responsibilities:
 *  - Track owner identity (PlayerProfile) and unique key (BarterStoreKey).
 *  - Maintain two inventories:
 *      * itemStacks: items for sale (27 slots), with a custom InventoryHolder that saves on close.
 *      * currencyHolder: received currency/bank inventory (27 slots), with a custom holder for bank semantics.
 *  - Maintain the current "price" of the trade as an ItemStack (not hard-coded currency).
 *  - Provide role-based checks and a styled shop title.

 * Notes:
 *  - getCurrentItemPrice()/setCurrentItemPrice clone() to avoid external mutation of the internal reference.
 *  - getNameStyled() resolves MiniMessage using config "styled-title" and a {player} placeholder.
 *  - canBreak(...) enforces both permission AND empty-inventories rule before allowing break.
 */
public class BarterStoreImpl implements BarterStore {

    /** Logical identity (typically wraps a UUID). */
    private final BarterStoreKey barterStoreKey;

    /** Owner profile; supports offline players via Paper's PlayerProfile. */
    private final PlayerProfile playerProfile;

    /** Inventory of items the shop is selling (27 slots). */
    private Inventory itemStacks;

    /** Inventory acting as the "bank" to store received currency (27 slots). */
    private final Inventory currencyHolder;

    /** The current price for trades, represented as an ItemStack. */
    private ItemStack itemStack;

    /** World locations associated with this shop (e.g., barrel block, attached sign, etc.). */
    private final List<Location> locations;

    /**
     * Constructs a store with existing content snapshots.
     *
     * @param barterStoreKey unique key for this store
     * @param playerProfile  owner profile
     * @param itemStacks     initial contents for the sale inventory
     * @param currencyItems  initial contents for the currency/bank inventory
     * @param itemStack      current price (as an ItemStack)
     * @param locations      physical world locations representing this store

     * Behavior:
     *  - Creates new 27-slot inventories with custom holders:
     *      * SaveOnClose(this, () -> this.itemStacks): holder used to trigger persistence when GUI closes.
     *      * BankOwner(this): holder indicating bank semantics (owner linkage, possible permission gating).
     *  - Copies provided lists into these inventories via setContents.
     */
    public BarterStoreImpl(BarterStoreKey barterStoreKey, PlayerProfile playerProfile, List<ItemStack> itemStacks, List<ItemStack> currencyItems, ItemStack itemStack, List<Location> locations) {
        this.barterStoreKey = barterStoreKey;
        this.playerProfile = playerProfile;
        this.locations = locations;
        this.itemStacks = Bukkit.createInventory(new SaveOnClose(this, () -> this.itemStacks), 27);
        this.itemStacks.setContents(itemStacks.toArray(new ItemStack[0]));
        this.currencyHolder = Bukkit.createInventory(new BankOwner(this), 27);
        this.currencyHolder.setContents(currencyItems.toArray(new ItemStack[0]));
        this.itemStack = itemStack;
    }

    /** Owner profile accessor. */
    @Override
    public PlayerProfile getPlayerProfile() {
        return this.playerProfile;
    }

    /** Unique key accessor. */
    @Override
    public BarterStoreKey getKey() {
        return this.barterStoreKey;
    }

    /** Sale inventory accessor (27 slots). */
    @Override
    public Inventory getSaleStorage() {
        return this.itemStacks;
    }

    /** Currency/bank inventory accessor (27 slots). */
    @Override
    public Inventory getCurrencyStorage() {
        return this.currencyHolder;
    }

    /**
     * Determines whether the player may break the shop block(s).

     * Rule:
     *  - Player must have the DELETE permission via role (owner/admin).
     *  - Both inventories (currency + sale items) must be empty; otherwise sends an error message.

     * UX:
     *  - When blocked due to non-empty inventories, sends a messages.yml-driven error and plays the error sound.
     */
    @Override
    public boolean canBreak(Player player) {
        boolean canDelete = this.getRole(player).hasPermission(BarterPermission.DELETE);
        if (canDelete) {
            boolean isEmpty = this.currencyHolder.isEmpty() && this.itemStacks.isEmpty();
            if (isEmpty) {
                return true;
            } else {
                // (migrated) Previously hard-coded:
                // player.sendMessage(Components.prefixedError(Component.text("You must empty both the Barter Bank and Shop Items.")));
                // Now localizable + styled via messages.yml + sound via Messages.error(...)
                Messages.error(player, "stores.break.must_empty");
                return false;
            }
        }

        return false;
    }

    /**
     * Styled shop name (e.g., inventory title), using MiniMessage and a {player} placeholder.
     * Pulls the template from config: configuration.getStyledBarterTitle().

     * Hardening:
     *  - If the configured template is null/blank, fall back to messages.yml → gui.main.title.
     *  - If messages.yml somehow returns blank, fall back to a safe literal: "<white><player></white>'s Barter Container".
     *  - If the owner profile name is null/blank, fall back to "Unknown".
     *  - Use UNPARSED placeholder for player name to avoid MiniMessage injection.
     */
    @Override
    public Component getNameStyled() {
        // Try config first
        String template = BarterContainer.INSTANCE.getConfiguration().getStyledBarterTitle();

        // Fallback to messages.yml if config is missing/blank
        if (template == null || template.isBlank()) {
            // Messages.get(...) never returns null; it returns the key itself if not found.
            template = com.stifflered.bartercontainer.util.Messages.get("gui.main.title");
        }

        // Final guard: ensure MiniMessage never sees null/blank
        if (template == null || template.isBlank() || "gui.main.title".equals(template)) {
            template = "<white><player></white>'s Barter Container";
        }

        // Resolve player name safely
        String ownerName = this.playerProfile.getName();
        if (ownerName == null || ownerName.isBlank()) {
            ownerName = "Unknown";
        }

        return MiniMessage.miniMessage().deserialize(
                template,
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("player", ownerName)
        );
    }

    /**
     * Current price as an ItemStack. Returns a clone to avoid external mutation of internal state.
     */
    @Override
    public ItemStack getCurrentItemPrice() {
        return this.itemStack.clone();
    }

    /**
     * Sets the current price; stores a clone to maintain encapsulation and prevent aliasing.
     */
    @Override
    public void setCurrentItemPrice(ItemStack itemStack) {
        this.itemStack = itemStack.clone();
    }

    /**
     * Simple role resolution:
     *  - Admin perm "barterchests.admin" => UPKEEPER
     *  - Owner (UUID match) => UPKEEPER
     *  - Otherwise => VISITOR

     * Implication:
     *  - UPKEEPER is the highest role (has DELETE, etc.).
     *  - This method centralizes permission-to-role mapping for store operations.
     */
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

    /** Returns the list of world locations tied to this store (e.g., primary container + adjunct blocks). */
    @Override
    public List<Location> getLocations() {
        return this.locations;
    }

}
