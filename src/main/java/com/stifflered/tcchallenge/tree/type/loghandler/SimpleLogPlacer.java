package com.stifflered.tcchallenge.tree.type.loghandler;

import com.stifflered.tcchallenge.tree.Tree;
import com.stifflered.tcchallenge.tree.TreeManager;
import com.stifflered.tcchallenge.tree.root.RootManager;
import com.stifflered.tcchallenge.tree.root.type.MutableRoot;
import com.stifflered.tcchallenge.tree.root.type.Root;
import com.stifflered.tcchallenge.tree.type.palette.TreePalette;
import com.stifflered.tcchallenge.tree.world.TreeWorld;
import com.stifflered.tcchallenge.util.Components;
import com.stifflered.tcchallenge.util.NeighboringLocationIterator;
import net.kyori.adventure.text.Component;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

public class SimpleLogPlacer implements LogHandler {

    private static final Component OTHER_NEARBY_ROOTS = Components.prefixedError(Component.text("You can't place a root nearby other tree roots!"));

    private final TreePalette palette;

    public SimpleLogPlacer(TreePalette palette) {
        this.palette = palette;
    }

    @Override
    public boolean onPlace(Root root, BlockPlaceEvent event) {
        Block placed = event.getBlock();
        Block against = event.getBlockAgainst();
        Player player = event.getPlayer();

//        // Causes the roots to "connect"
//        if (player.isSneaking()) {
//            if (this.palette.logs().contains(against.getType()) && placed.getBlockData() instanceof Orientable placedData) {
//                if (against.getBlockData() instanceof Orientable orientable) {
//                    Axis axis = orientable.getAxis();
//                    Axis placedAxis = placedData.getAxis();
//                    if (placedAxis != axis) {
//                        against.setType(this.palette.woodLog());
//                        orientable = (Orientable) against.getBlockData();
//
//                        orientable.setAxis(axis);
//                        against.setBlockData(orientable);
//                    }
//                }
//            }
//        }

        Location placeLocation = event.getBlockPlaced().getLocation();

        Tree tree = TreeManager.INSTANCE.getTreeIfLoaded(player);
        RootManager rootManager = TreeWorld.of(placed.getWorld()).getRootManager();
        // Find parallel roots, not allowing to place if there are nearby ones
        for (Location location : new NeighboringLocationIterator(placeLocation)) {
            Root neighboringRoot = rootManager.getRoot(location);
            if (neighboringRoot != null && !neighboringRoot.getKey().equals(tree.getKey())) {
                player.sendMessage(OTHER_NEARBY_ROOTS);
                return false;
            }
        }

        rootManager.putRoot(placeLocation, new MutableRoot(tree.getKey()));

        return true;
    }

    @Override
    public boolean onBreak(Root root, BlockBreakEvent event) {
        Location blockLocation = event.getBlock().getLocation();

        TreeWorld.of(blockLocation.getWorld()).getRootManager().removeRoot(blockLocation);
        return true;
    }

    @Override
    public boolean onDropItem(Root root, BlockDropItemEvent event) {
        List<Item> items = event.getItems();
        if (items.isEmpty()) {
            return true;
        }

        Item item = event.getItems().get(0);
        if (event.getBlockState().getType() == this.palette.woodLog()) {
            item.setItemStack(new ItemStack(this.palette.mainLog()));
        }

        return true;
    }

    @Override
    public Set<Material> validLogs() {
        return this.palette.logs();
    }
}
