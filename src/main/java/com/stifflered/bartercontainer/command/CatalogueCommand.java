package com.stifflered.bartercontainer.command;

import com.stifflered.bartercontainer.gui.catalogue.*;
import com.stifflered.bartercontainer.util.Components;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CatalogueCommand extends BukkitCommand {

    public CatalogueCommand() {
        super("catalog");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        Player player = (Player) sender;
        if (!sender.hasPermission("barterchests.catalog")) {
            sender.sendMessage(Components.NO_PERMISSION);
            return true;
        }

        new CatalogueGui().show(player);
        return true;
    }
}
