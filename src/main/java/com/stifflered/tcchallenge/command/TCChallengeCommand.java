package com.stifflered.tcchallenge.command;

import com.destroystokyo.paper.MaterialSetTag;
import com.stifflered.tcchallenge.events.TreeCreateEvent;
import com.stifflered.tcchallenge.tree.OnlineTree;
import com.stifflered.tcchallenge.tree.Tree;
import com.stifflered.tcchallenge.tree.TreeManager;
import com.stifflered.tcchallenge.tree.root.RootManager;
import com.stifflered.tcchallenge.tree.root.type.MutableRoot;
import com.stifflered.tcchallenge.tree.type.TreeType;
import com.stifflered.tcchallenge.tree.world.TreeWorld;
import com.stifflered.tcchallenge.util.Components;
import com.stifflered.tcchallenge.util.scanner.BlockScanner;
import com.stifflered.tcchallenge.util.scanner.BlockScannerContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TCChallengeCommand extends BukkitCommand {

    public TCChallengeCommand() {
        super("barterchests");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        Player player = (Player) sender;
        if (!sender.hasPermission("barterchests.admin.gui")) {
            sender.sendMessage(Components.NO_PERMISSION);
            return true;
        }

        return true;
    }
}
