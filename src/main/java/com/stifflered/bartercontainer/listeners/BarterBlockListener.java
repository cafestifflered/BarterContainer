package com.stifflered.bartercontainer.listeners;

import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.barter.permission.BarterRole;
import com.stifflered.bartercontainer.gui.tree.BarterBuyGui;
import com.stifflered.bartercontainer.gui.tree.BarterGui;
import com.stifflered.bartercontainer.item.ItemInstances;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.stifflered.bartercontainer.util.Sounds;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class BarterBlockListener implements Listener {

    @EventHandler
    public void protect(BlockBreakEvent event) {
        Location location = event.getBlock().getLocation();
        BarterStore barterStore = BarterManager.INSTANCE.getBarter(location);
        if (barterStore != null) {
            event.setCancelled(!barterStore.canBreak(event.getPlayer()));
            if (!event.isCancelled()) {
                BarterManager.INSTANCE.removeBarter(location);
                ItemUtil.giveItemOrThrow(event.getPlayer(), ItemInstances.SHOP_LISTER_ITEM.getItem());
            }
        }
    }

    @EventHandler
    public void drop(BlockExplodeEvent event) {
        event.blockList().removeIf((block) -> { return BarterManager.INSTANCE.getBarter(block.getLocation()) != null;});
    }

    @EventHandler
    public void drop(EntityExplodeEvent event) {
        event.blockList().removeIf((block) -> { return BarterManager.INSTANCE.getBarter(block.getLocation()) != null;});
    }

    @EventHandler
    public void change(InventoryMoveItemEvent event) {
        if (event.getDestination().getHolder(false) instanceof Hopper hopper) {
            BarterStore barterStore = BarterManager.INSTANCE.getBarter(hopper.getLocation());
            if (barterStore != null) {
                event.setCancelled(true);
            }
        }

        if (event.getSource().getHolder(false) instanceof Hopper hopper) {
            BarterStore barterStore = BarterManager.INSTANCE.getBarter(hopper.getLocation());
            if (barterStore != null) {
                event.setCancelled(true);
            }
        }
    }
//
//    @EventHandler
//    public void fade(BlockFadeEvent event) {
//        ImportantBlock importantBlock = this.fromLocation(event.getBlock().getLocation());
//        if (importantBlock != null) {
//            event.setCancelled(true);
//        }
//    }
//
//    @EventHandler
//    public void fade(BlockFromToEvent event) {
//        ImportantBlock importantBlock = this.fromLocation(event.getBlock().getLocation());
//        if (importantBlock != null) {
//            event.setCancelled(true);
//        }
//    }
//
//    @EventHandler
//    public void spread(BlockSpreadEvent event) {
//        ImportantBlock importantBlock = this.fromLocation(event.getBlock().getLocation());
//        if (importantBlock != null) {
//            event.setCancelled(true);
//        }
//    }
//
//    @EventHandler
//    public void form(BlockFormEvent event) {
//        ImportantBlock importantBlock = this.fromLocation(event.getBlock().getLocation());
//        if (importantBlock != null) {
//            event.setCancelled(true);
//        }
//    }
//
//    @EventHandler
//    public void piston(BlockPistonExtendEvent event) {
//        for (Block block : event.getBlocks()) {
//            ImportantBlock importantBlock = this.fromLocation(block.getLocation());
//            if (importantBlock != null) {
//                event.setCancelled(true);
//            }
//        }
//    }
//
//    @EventHandler
//    public void piston(BlockPistonRetractEvent event) {
//        for (Block block : event.getBlocks()) {
//            ImportantBlock importantBlock = this.fromLocation(block.getLocation());
//            if (importantBlock != null) {
//                event.setCancelled(true);
//            }
//        }
//    }
//

    @EventHandler
    public void interact(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && !event.getPlayer().isSneaking()) {
            return;
        }
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (block == null) {
            return;
        }

        BarterStore barterStore = BarterManager.INSTANCE.getBarter(block.getLocation());
        if (barterStore != null) {
            Sounds.openChest(player);
            if (barterStore.getRole(player) == BarterRole.UPKEEPER || player.hasPermission("barterchests.edit_all")) {
                if (player.isSneaking()) {
                    new BarterBuyGui(player, barterStore).show(player);
                } else {
                    new BarterGui(barterStore).show(player);
                }
                event.setUseInteractedBlock(Event.Result.DENY);
            } else {
                new BarterBuyGui(player, barterStore).show(player);
                event.setUseInteractedBlock(Event.Result.DENY);
            }
        }
    }


}
