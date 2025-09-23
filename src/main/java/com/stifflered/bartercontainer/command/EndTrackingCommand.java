package com.stifflered.bartercontainer.command;

import com.stifflered.bartercontainer.util.Messages;
import com.stifflered.bartercontainer.util.TrackingManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Command: /endtracking

 * Purpose:
 *  - Stops any active tracking session for the executing player.
 *  - Delegates to TrackingManager to remove them from being tracked (e.g., stop arrow guidance).

 * Permissions:
 *  - None (available to everyone by default).

 * Notes:
 *  - Registered via plugin.yml.
 *  - Directly casts CommandSender → Player.
 *  - Simple, one-shot command: no extra feedback.
 */
public class EndTrackingCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        // Guard: players only
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.mm("commands.common.players_only"));
            return true;
        }

        TrackingManager.instance().untrack(player);
        return true;
    }
}
