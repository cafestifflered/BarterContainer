package com.stifflered.tcchallenge.tree.type.palette;

import org.bukkit.Material;

import java.util.Set;

public record TreePalette(
        String name,
        Material sapling,
        Material mainLog,
        Material woodLog,
        Set<Material> logs,
        Material leave,
        Material icon
) {
}
