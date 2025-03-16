package com.stifflered.bartercontainer.command;

import com.stifflered.bartercontainer.item.ItemInstances;
import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.TrackingManager;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class EndTrackingCommand extends BukkitCommand {

    public EndTrackingCommand() {
        super("end-tracking");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        TrackingManager.instance().untrack((Player) sender);
        return true;
    }
}
