package com.stifflered.bartercontainer.item.impl;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.barter.BarterStoreKeyImpl;
import com.stifflered.bartercontainer.item.ItemInstance;
import com.stifflered.bartercontainer.store.BarterStoreImpl;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.stifflered.bartercontainer.util.Messages;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Custom admin/player tool (a {@link ItemInstance}) that allows
 * creation of new barter shops by "listing" a container as a shop.

 * Purpose:
 * - Converts an empty barrel into a barter shop when right-clicked
 *   with this special item.

 * Behavior:
 * - Requires the permission "barterchests.create".
 * - Only works on barrels (not other container types).
 * - The target barrel must be empty to prevent item loss.
 * - Prevents double-registration if a barter shop already exists
 *   at that location.

 * Outcome:
 * - A new {@link com.stifflered.bartercontainer.store.BarterStore} is created
 *   and persisted through {@link BarterManager}.
 * - Player receives success feedback (chat message + sound).
 * - One "Shop Lister" item is consumed from their hand.

 * Internationalization (messages.yml):
 *   items.shop_lister.invalid_block
 *   commands.common.no_permission
 *   items.shop_lister.must_be_empty
 *   items.shop_lister.already_exists
 *   items.shop_lister.success
 */
public class ShopListerItem extends ItemInstance {

    // Message keys (constants for clarity)
    private static final String KEY_INVALID_BLOCK = "items.shop_lister.invalid_block";
    private static final String KEY_NO_PERMISSION = "commands.common.no_permission";
    private static final String KEY_MUST_BE_EMPTY = "items.shop_lister.must_be_empty";
    private static final String KEY_ALREADY_EXISTS = "items.shop_lister.already_exists";
    private static final String KEY_SUCCESS = "items.shop_lister.success";

    // Display keys (name + lore for this tool; pulled from messages.yml)
    private static final String KEY_ITEM_NAME = "items.shop_lister.item_name";
    private static final String KEY_ITEM_LORE = "items.shop_lister.item_lore";

    /** Sound played on successful shop creation. */
    private static final Sound ACTIVATED_SOUND =
            Sound.sound(org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, Sound.Source.BLOCK, 1, 1);

    /**
     * Constructor:
     * - Registers this item as "shop_lister".
     * - Uses item appearance/configuration defined in config.yml
     *   via {@link BarterContainer#getConfiguration()}.
     */
    public ShopListerItem() {
        super("shop_lister", BarterContainer.INSTANCE.getConfiguration().getShopListerConfiguration());
    }

    /**
     * Ensure the item stack uses the display name/lore from messages.yml.
     * Kept minimal and idempotent; called at event-time so even old stacks get fixed.
     */
    private static void ensureMessagesDisplay(ItemStack stack) {
        if (stack == null) return;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        Component name = Messages.mm(KEY_ITEM_NAME);
        meta.displayName(name);

        List<Component> lore = Messages.mmList(KEY_ITEM_LORE);
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }

        stack.setItemMeta(meta);
    }

    /**
     * Called when player right-clicks a block while holding this item.

     * Flow:
     *  1. Cancel vanilla item usage.
     *  2. Validate clicked block:
     *     - Must not be null.
     *     - Player must have permission "barterchests.create".
     *     - Must be a barrel (other containers are disallowed).
     *  3. If the block is a TileState:
     *     - Ensure barrel inventory is empty.
     *     - Ensure no existing shop already registered here.
     *     - Create a new {@link BarterStoreImpl} with:
     *         • random UUID as key,
     *         • player as owner,
     *         • empty sale & currency inventories,
     *         • default price item (currently diamond),
     *         • the clicked barrel’s location.
     *     - Register and persist store through {@link BarterManager}.
     *     - Provide success feedback + play sound.
     *     - Consume one Shop Lister item from player’s hand.
     *  4. Otherwise, send invalid block message.
     */
    @Override
    public void interact(PlayerInteractEvent event) {
        event.setUseItemInHand(Event.Result.DENY);

        // Ensure the held item shows the messages.yml-driven display (name + lore)
        ensureMessagesDisplay(event.getItem());

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) {
            Messages.send(player, KEY_INVALID_BLOCK);
            return;
        }
        if (!player.hasPermission("barterchests.create")) {
            Messages.send(player, KEY_NO_PERMISSION);
            return;
        }

        if (block.getType() != Material.BARREL) {
            Messages.send(player, KEY_INVALID_BLOCK);
            return;
        }

        BlockState state = block.getState(false);
        if (state instanceof TileState tileState) {
            // Prevent converting containers with existing contents
            if (tileState instanceof Container container && !container.getInventory().isEmpty()) {
                Messages.send(player, KEY_MUST_BE_EMPTY);
                return;
            }
            // Prevent creating duplicate shops at same location
            if (BarterManager.INSTANCE.getBarter(tileState.getLocation()).isPresent()) {
                Messages.send(player, KEY_ALREADY_EXISTS);
                return;
            }

            // Create new barter shop
            List<org.bukkit.Location> locationList = new ArrayList<>();
            locationList.add(state.getLocation());

            BarterManager.INSTANCE.createNewStore(
                    new BarterStoreImpl(
                            new BarterStoreKeyImpl(UUID.randomUUID()),
                            player.getPlayerProfile(),
                            new ArrayList<>(),
                            new ArrayList<>(),
                            new ItemStack(Material.DIAMOND), // default item price placeholder
                            locationList
                    ),
                    tileState.getPersistentDataContainer(),
                    state.getChunk()
            );

            // Feedback to player
            Messages.send(player, KEY_SUCCESS);
            player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_WORK_MASON, Sound.Source.BLOCK, 1, 1));

            // Consume the Shop Lister item
            ItemUtil.subtract(event.getPlayer(), event.getItem());
            event.setUseInteractedBlock(Event.Result.DENY);
        } else {
            Messages.send(player, KEY_INVALID_BLOCK);
        }
    }
}
