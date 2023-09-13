package com.stifflered.tcchallenge.listeners;

import com.stifflered.tcchallenge.tree.OnlineTree;
import com.stifflered.tcchallenge.tree.TreeManager;
import com.stifflered.tcchallenge.tree.root.RootManager;
import com.stifflered.tcchallenge.tree.root.type.Root;
import com.stifflered.tcchallenge.tree.type.loghandler.LogHandler;
import com.stifflered.tcchallenge.tree.util.TreePermissions;
import com.stifflered.tcchallenge.tree.world.TreeWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class LogListener implements Listener {

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        OnlineTree tree = TreeManager.INSTANCE.getTreeIfLoaded(player);
        Root root = TreeWorld.of(player.getWorld()).getRootManager().getRoot(event.getBlock().getLocation());

        // TODO: Lookup tree, allow players to interact with other trees fine
        if (tree != null && event.canBuild()) {
            LogHandler placer = tree.getType().getPlacer();
            if (placer.validLogs().contains(event.getBlock().getType())) {
                event.setBuild(placer.onPlace(root, event));
            }
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Root root = TreeWorld.of(player.getWorld()).getRootManager().getRoot(event.getBlock().getLocation());

        if (root != null && !TreePermissions.hasPermission(player, root.getKey())) {
            TreePermissions.noPermission(player);
            event.setCancelled(true);
            return;
        }

        OnlineTree tree = TreeManager.INSTANCE.getTreeIfLoaded(player);
        // TODO: Lookup tree, allow players to interact with other trees fine
        if (tree != null && !event.isCancelled()) {
            LogHandler placer = tree.getType().getPlacer();
            if (placer.validLogs().contains(event.getBlock().getType())) {
                event.setCancelled(!placer.onBreak(root, event));
            }
        }
    }

    @EventHandler
    public void onDrop(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        OnlineTree tree = TreeManager.INSTANCE.getTreeIfLoaded(player);
        Root root = TreeWorld.of(player.getWorld()).getRootManager().getRoot(event.getBlock().getLocation());

        // TODO: Lookup tree, allow players to interact with other trees fine
        if (tree != null && !event.isCancelled()) {
            LogHandler placer = tree.getType().getPlacer();
            if (placer.validLogs().contains(event.getBlockState().getType())) {
                event.setCancelled(!placer.onDropItem(root, event));
            }
        }
    }
}
