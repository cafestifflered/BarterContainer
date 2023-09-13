package com.stifflered.tcchallenge.tree.world;

import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class TreeWorldRegistry {

    private static final Map<World, TreeWorld> TREE_WORLD_MAP = new HashMap<>();

    @Nullable
    public static TreeWorld of(World world) {
        return TREE_WORLD_MAP.get(world);
    }

    public static void register(World world, TreeWorld treeWorld) {
        TREE_WORLD_MAP.put(world, treeWorld);
    }

    public static void unregister(World world) {
        TREE_WORLD_MAP.remove(world);
    }
}
