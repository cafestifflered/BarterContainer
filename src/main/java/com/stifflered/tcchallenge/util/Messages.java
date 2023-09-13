package com.stifflered.tcchallenge.util;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public class Messages {

    public static void error(Player player, Component message) {
        player.sendMessage(message);
        Sounds.error(player);
    }
}
