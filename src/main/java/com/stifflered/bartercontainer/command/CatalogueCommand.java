package com.stifflered.bartercontainer.command;

import com.stifflered.bartercontainer.gui.catalogue.CatalogueGui;
import com.stifflered.bartercontainer.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Command: /catalog

 * Purpose:
 *  - Opens the catalogue GUI for the player, showing available barter shops/items.

 * Permissions:
 *  - Requires "barterchests.catalog". If missing, sends NO_PERMISSION message.

 * Notes:
 *  - Registered via plugin.yml.
 *  - Directly casts CommandSender → Player (not safe for console).
 *  - GUI handling delegated to {@link CatalogueGui}.
 */
public class CatalogueCommand implements CommandExecutor {

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

        if (!sender.hasPermission("barterchests.catalog")) {
            sender.sendMessage(Messages.mm("commands.common.no_permission"));
            return true;
        }

        new CatalogueGui().show(player);
        return true;
    }
}
