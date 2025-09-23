package com.stifflered.bartercontainer.item.impl;

import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.item.ItemInstance;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.stifflered.bartercontainer.util.Messages;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Special admin-only tool (a custom {@link ItemInstance}) for repairing barter shops.

 * Purpose:
 * - Acts as a "fixer stick" to resolve broken shops that have missing block
 *   location data but still exist in storage.

 * Behavior:
 * - Only works for players with "barterchests.admin" permission.
 * - Must right-click on a valid container block that should be part of a shop.
 * - If the shop exists but has an empty location list, it reattaches the block's
 *   location to the shop.
 * - Provides feedback to the admin via messages + sounds.

 * Notes:
 * - Uses the ItemInstance framework (persistent custom item type).
 * - Registered in {@link com.stifflered.bartercontainer.item.ItemInstances}.

 * Internationalization (messages.yml):
 *   items.shop_fixer.item_name
 *   items.shop_fixer.invalid_block
 *   commands.common.no_permission
 *   items.shop_fixer.fixed_location
 */
public class ShopFixerItem extends ItemInstance {

    // Message keys (kept as constants for reuse/readability)
    private static final String KEY_INVALID_BLOCK = "items.shop_fixer.invalid_block";
    private static final String KEY_NO_PERMISSION = "commands.common.no_permission";
    private static final String KEY_FIXED_LOCATION = "items.shop_fixer.fixed_location";
    private static final String KEY_ITEM_NAME = "items.shop_fixer.item_name";

    /** Sound that was previously used as a small activation cue. */
    @SuppressWarnings("unused")
    private static final Sound ACTIVATED_SOUND =
            Sound.sound(org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, Sound.Source.BLOCK, 1, 1);

    /**
     * Constructor:
     * - Registers this as an ItemInstance under key "shop_fixer".
     * - Creates the underlying ItemStack (a STICK named from messages.yml).
     */
    public ShopFixerItem() {
        super("shop_fixer", ItemUtil.wrapEdit(new ItemStack(Material.STICK), (meta) -> {
            // Name is now MiniMessage-driven via messages.yml
            Component displayName = Messages.mm(KEY_ITEM_NAME);
            Components.name(meta, displayName);
        }));
    }

    /**
     * Called when a player right-clicks with the fixer stick.

     * Flow:
     *  1. Deny vanilla item use.
     *  2. Get clicked block. If null → send invalid block message.
     *  3. Check permission ("barterchests.admin"). If missing → deny.
     *  4. Look up if block belongs to a BarterStore.
     *     - If store exists AND has no locations, add this block as its location.
     *     - Send success feedback (message + villager mason work sound).
     *  5. Save the store via BarterManager.
     *  6. If no store was found, send invalid block message.
     */
    @Override
    public void interact(PlayerInteractEvent event) {
        event.setUseItemInHand(Event.Result.DENY);

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) {
            Messages.send(player, KEY_INVALID_BLOCK);
            return;
        }
        if (!player.hasPermission("barterchests.admin")) {
            Messages.send(player, KEY_NO_PERMISSION);
            return;
        }

        Optional<BarterStore> storeOptional = BarterManager.INSTANCE.getBarter(block.getLocation());
        if (storeOptional.isPresent()) {
            BarterStore store = storeOptional.get();
            if (store.getLocations().isEmpty()) {
                // Success via messages.yml
                Messages.send(player, KEY_FIXED_LOCATION);
                player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_WORK_MASON, Sound.Source.BLOCK, 1, 1));
                store.getLocations().add(block.getLocation());
            }

            event.setUseInteractedBlock(Event.Result.DENY);
            BarterManager.INSTANCE.save(store);
        } else {
            Messages.send(player, KEY_INVALID_BLOCK);
        }
    }
}
