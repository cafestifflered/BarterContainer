package com.stifflered.bartercontainer.listeners;

import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.barter.permission.BarterPermission;
import com.stifflered.bartercontainer.barter.permission.BarterRole;
import com.stifflered.bartercontainer.barter.serializers.LegacyBarterSerializer;
import com.stifflered.bartercontainer.gui.tree.BarterBuyGui;
import com.stifflered.bartercontainer.gui.tree.BarterGui;
import com.stifflered.bartercontainer.item.ItemInstances;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.stifflered.bartercontainer.util.Sounds;
import com.stifflered.bartercontainer.util.Messages;
import com.stifflered.bartercontainer.BarterContainer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener for block-level interactions that affect barter containers.

 * Responsibilities:
 *  - Prevent unauthorized breaking or accidental loss of shop blocks.
 *  - Protect shops from explosion drop lists.
 *  - Block hopper automation to/from shop blocks.
 *  - Handle right-click interactions to open GUIs (owner vs visitor behavior),
 *    including a legacy “one-time migration” path that dismantles old-format barrels
 *    and refunds items + lister.

 * Notes:
 *  - Uses BarterManager to resolve whether a clicked/broken block is a shop and to
 *    remove/load shops as needed.
 *  - Differentiates roles via BarterRole/BarterPermission.
 *  - GUI split:
 *      * UPKEEPER (or edit_all perm): sneaking → open buy UI; otherwise open management UI.
 *      * Others: open buy UI only.

 * Internationalization:
 *  - All player-facing strings are provided by messages.yml:
 *      listeners.barter_block.legacy_recreate
 *      listeners.barter_block.legacy_drop_warning
 *      listeners.barter_block.force_loading
 */
public class BarterBlockListener implements Listener {

    /** Supports migration: reads old PDC format to detect legacy barrels and convert/clear them. */
    private static final LegacyBarterSerializer legacy = new LegacyBarterSerializer();

    /**
     * Prevents breaking a shop unless the player has DELETE permission and both inventories are empty.
     * If allowed, removes the store and returns a new Shop Lister item to the breaker.
     */
    @EventHandler
    public void protect(BlockBreakEvent event) {
        Location location = event.getBlock().getLocation();
        BarterManager.INSTANCE.getBarter(location)
                .ifPresent((container) -> {
                    event.setCancelled(!container.canBreak(event.getPlayer()));
                    if (!event.isCancelled()) {
                        BarterManager.INSTANCE.removeBarterContainer(location);
                        ItemUtil.giveItemOrThrow(event.getPlayer(), ItemInstances.SHOP_LISTER_ITEM.getItem());
                    }
                });
    }

    /**
     * If an explosion drops blocks that are barter containers, suppress those drops
     * (prevents dupe/loss scenarios; block itself may still break depending on other logic).
     */
    @EventHandler
    public void drop(BlockExplodeEvent event) {
        event.blockList().removeIf((block) -> BarterManager.INSTANCE.getBarter(block.getLocation()).isPresent());
    }

    /** Same drop suppression for entity-caused explosions (creepers, TNT, etc.). */
    @EventHandler
    public void drop(EntityExplodeEvent event) {
        event.blockList().removeIf((block) -> BarterManager.INSTANCE.getBarter(block.getLocation()).isPresent());
    }

    /**
     * Prevent hopper automation from pushing/pulling items to/from a barter container block.
     * Checks both destination and source hoppers and cancels transfers if they coincide with a shop.
     */
    @EventHandler
    public void change(InventoryMoveItemEvent event) {
        if (event.getDestination().getHolder(false) instanceof Hopper hopper) {
            BarterManager.INSTANCE.getBarter(hopper.getLocation())
                    .ifPresent((container) -> event.setCancelled(true));
        }

        if (event.getSource().getHolder(false) instanceof Hopper hopper) {
            BarterManager.INSTANCE.getBarter(hopper.getLocation())
                    .ifPresent((container) -> event.setCancelled(true));
        }
    }

    //    Various world-protection hooks were prototyped here (fade/spread/form/piston).
    //    If re-enabled, they would prevent environmental moves/changes on “important blocks” (shop parts).

    /**
     * Right-click handling for shop blocks. Only intercepts when the clicked block is our (known or likely) shop.
     * Leaves vanilla interaction and block placement alone everywhere else.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        // Only interested in right-clicking a block.
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        final Block block = event.getClickedBlock();
        final Player player = event.getPlayer();
        if (block == null) return;

        // ---- 1) Legacy PDC migration path ----
        if (block.getState(false) instanceof TileState state) {
            BarterStore legacyStore = legacy.getBarterStore(state.getPersistentDataContainer());
            if (legacyStore != null && legacyStore.getRole(player).hasPermission(BarterPermission.DELETE)) {
                block.setType(Material.AIR);

                Messages.send(player, "listeners.barter_block.legacy_recreate");
                Messages.send(player, "listeners.barter_block.legacy_drop_warning");

                player.getInventory().addItem(ItemInstances.SHOP_LISTER_ITEM.getItem());

                for (ItemStack item : legacyStore.getSaleStorage()) {
                    if (item != null) block.getWorld().dropItemNaturally(block.getLocation(), item);
                }
                for (ItemStack item : legacyStore.getCurrencyStorage()) {
                    if (item != null) block.getWorld().dropItemNaturally(block.getLocation(), item);
                }
                // Legacy handled; do not proceed with shop UI.
                return;
            }
        }

        // ---- 2) Fast bail-out unless this is (known or likely) a shop block ----
        final Location loc = block.getLocation();
        final boolean isKnownShop = BarterManager.INSTANCE.getBarter(loc).isPresent();

        // Heuristic: our shops are barrels (expand if you support more types)
        final boolean looksLikeShopBlock = (block.getType() == Material.BARREL);

        if (!isKnownShop && !looksLikeShopBlock) {
            // Not our concern — allow vanilla interaction & block placement
            return;
        }

        // ---- 3) From here on, we own the interaction for shops ----
        // Prevent vanilla opening/placement on shops
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        // Kick off async load (if needed) and open GUI when ready
        var future = BarterManager.INSTANCE.getOrLoadAsync(loc);

        // Debounce the "loading..." message so it only appears if slow
        final long delayTicks = 10L; // ~0.5s
        var loadingPing = Bukkit.getScheduler().runTaskLater(BarterContainer.INSTANCE, () -> {
            if (!future.isDone() && player.isOnline()) {
                Messages.send(player, "listeners.barter_block.force_loading");
            }
        }, delayTicks);

        future.thenAccept(storeOpt -> {
            try { loadingPing.cancel(); } catch (Throwable ignored) {}

            Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> {
                if (!player.isOnline()) return;

                storeOpt.ifPresent(store -> {
                    Sounds.openChest(player);
                    boolean canEdit = (store.getRole(player) == BarterRole.UPKEEPER)
                            || player.hasPermission("barterchests.edit_all");
                    if (canEdit) {
                        if (player.isSneaking()) {
                            new BarterBuyGui(player, store).show(player);
                        } else {
                            new BarterGui(store).show(player);
                        }
                    } else {
                        new BarterBuyGui(player, store).show(player);
                    }
                });
            });
        });
    }
}
