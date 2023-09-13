package com.stifflered.tcchallenge.tree.util;

import com.stifflered.tcchallenge.tree.TreeKey;
import com.stifflered.tcchallenge.util.Components;
import com.stifflered.tcchallenge.util.Sounds;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public class TreePermissions {

    private static final Component NO_PERMISSION = Components.prefixedError(Component.text("You don't have permission to do this!"));

    public static boolean hasPermission(Player player, TreeKey key) {
        return key.getKey().equals(player.getUniqueId());
    }

    public static void noPermission(Player player) {
        player.sendMessage(NO_PERMISSION);
        Sounds.error(player);
    }
}
