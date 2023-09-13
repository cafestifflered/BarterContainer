package com.stifflered.tcchallenge.tree.util;

import com.destroystokyo.paper.MaterialSetTag;
import com.stifflered.tcchallenge.tree.TreeKey;
import com.stifflered.tcchallenge.tree.root.RootManager;
import com.stifflered.tcchallenge.tree.root.type.Root;
import com.stifflered.tcchallenge.tree.world.TreeWorld;
import com.stifflered.tcchallenge.util.Components;
import com.stifflered.tcchallenge.util.NeighboringLocationIterator;
import com.stifflered.tcchallenge.util.Sounds;
import com.stifflered.tcchallenge.util.TagUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class TreeProtection {

    private static final Set<Material> PROTECTED_BLOCKS = new MaterialSetTag(TagUtil.of("protected_blocks"))
            .add(Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL)
            .add(Material.DISPENSER, Material.DROPPER, Material.DAYLIGHT_DETECTOR)
            .add(Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER)
            .add(Tag.ANVIL.getValues())
            .add(Tag.CAMPFIRES.getValues())
            .add(Tag.SIGNS.getValues())
            .getValues();
    private static final Component PROTECTED_BLOCK = Components.prefixedError(Component.text("You can't use this block as it's protected by a tree!"));

    @Nullable
    public static TreeKey getInnherietlyTreeProtection(Location location, BlockData blockData) {
        if (!PROTECTED_BLOCKS.contains(blockData.getMaterial())) {
            return null;
        }

        RootManager rootManager = TreeWorld.of(location.getWorld()).getRootManager();
        for (Location neighbor : new NeighboringLocationIterator(location)) {
            Root root = rootManager.getRoot(neighbor);
            if (root != null) {
                return root.getKey();
            }
        }

        return null;
    }

    public static void errorProtectedBlock(Player player) {
        player.sendMessage(PROTECTED_BLOCK);
        Sounds.error(player);
    }
}
