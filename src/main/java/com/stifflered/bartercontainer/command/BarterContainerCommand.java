package com.stifflered.bartercontainer.command;

import com.stifflered.bartercontainer.item.ItemInstances;
import com.stifflered.bartercontainer.util.Components;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BarterContainerCommand extends BukkitCommand {

    public BarterContainerCommand() {
        super("barterbarrels");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        Player player = (Player) sender;
        if (!sender.hasPermission("barterchests.admin")) {
            sender.sendMessage(Components.NO_PERMISSION);
            return true;
        }

        player.getInventory().addItem(ItemInstances.SHOP_LISTER_ITEM.getItem());
        player.getInventory().addItem(ItemInstances.SHOP_FIXER_ITEM.getItem());
        return true;
    }
}
