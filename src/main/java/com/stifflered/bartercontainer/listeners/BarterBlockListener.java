package com.stifflered.bartercontainer.listeners;

import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.barter.permission.BarterRole;
import com.stifflered.bartercontainer.gui.tree.BarterBuyGui;
import com.stifflered.bartercontainer.gui.tree.BarterGui;
import com.stifflered.bartercontainer.store.BarterStore;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class BarterBlockListener implements Listener {

    @EventHandler
    public void protect(BlockBreakEvent event) {
        BarterStore barterStore = BarterManager.INSTANCE.getBarter(event.getBlock().getLocation());
        if (barterStore != null) {
            event.setCancelled(barterStore.canBreak(event.getPlayer()));
        }
    }
//
//    @EventHandler
//    public void drop(BlockDropItemEvent event) {
//        ImportantBlock importantBlock = this.fromLocation(event.getBlock().getLocation());
//        if (importantBlock != null) {
//            importantBlock.mutateDrops(event.getPlayer(), event.getItems());
//        }
//    }
//
//    @EventHandler
//    public void change(EntityChangeBlockEvent event) {
//        ImportantBlock importantBlock = this.fromLocation(event.getBlock().getLocation());
//        if (importantBlock != null) {
//            event.setCancelled(true);
//        }
//    }
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
            if (barterStore.getRole(player) == BarterRole.UPKEEPER || player.hasPermission("barterchests.edit_all")) {
                if (player.isSneaking()) {
                    new BarterBuyGui(player, barterStore).show(player);
                } else {
                    new BarterGui(barterStore).show(player);
                }
            } else {
                new BarterBuyGui(player, barterStore).show(player);
            }
        }
    }


}
