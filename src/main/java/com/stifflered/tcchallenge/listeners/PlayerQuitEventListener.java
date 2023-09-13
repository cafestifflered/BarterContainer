package com.stifflered.tcchallenge.listeners;

import com.stifflered.tcchallenge.tree.OnlineTree;
import com.stifflered.tcchallenge.tree.TreeManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitEventListener implements Listener {

    @EventHandler
    public void onQuitEvent(PlayerQuitEvent event) {
        // Save your tree
        OnlineTree tree = TreeManager.INSTANCE.getTreeIfLoaded(event.getPlayer());
        if (tree != null) {
            TreeManager.INSTANCE.saveTree(tree);
            TreeManager.INSTANCE.getStorageManager().downgradeTree(tree);
        }
    }

}
