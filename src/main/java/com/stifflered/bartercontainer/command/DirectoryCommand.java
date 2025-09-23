package com.stifflered.bartercontainer.commands;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.gui.directory.OwnerDirectoryGui;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DirectoryCommand implements CommandExecutor {
    private final BarterContainer plugin;

    public DirectoryCommand(BarterContainer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only."));
            return true;
        }

        new OwnerDirectoryGui(plugin, player).open();
        return true;
    }
}
