package com.stifflered.tcchallenge.listeners;

import com.stifflered.tcchallenge.tree.TreeKey;
import com.stifflered.tcchallenge.tree.util.TreePermissions;
import com.stifflered.tcchallenge.tree.util.TreeProtection;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

public class InteractEventListener implements Listener {

    @EventHandler
    public void interact(PlayerInteractEvent event) {
        Block clickedBlock = event.getClickedBlock();
        Player player = event.getPlayer();

        if (clickedBlock != null) {
            BlockData blockData = event.getClickedBlock().getBlockData();
            Location clickedBlockLocation = event.getClickedBlock().getLocation();

            if (event.getAction().isRightClick() && blockData instanceof Bed) {
                event.setUseInteractedBlock(Event.Result.DENY);
                return;
            }

            TreeKey key = TreeProtection.getInnherietlyTreeProtection(clickedBlockLocation, blockData);
            if (key != null && !TreePermissions.hasPermission(player, key)) {
                TreeProtection.errorProtectedBlock(player);
                event.setUseInteractedBlock(Event.Result.DENY);
                return;
            }

        }
    }
}
