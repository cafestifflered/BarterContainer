package com.stifflered.tcchallenge.tree.type.loghandler;

import com.stifflered.tcchallenge.tree.root.type.Root;
import org.bukkit.Material;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Set;

public interface LogHandler {

    boolean onPlace(Root root, BlockPlaceEvent event);

    boolean onBreak(Root root, BlockBreakEvent event);

    boolean onDropItem(Root root, BlockDropItemEvent event);

    Set<Material> validLogs();
}
