package com.stifflered.tcchallenge.tree;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.stifflered.tcchallenge.tree.childtree.TreeNodeManager;
import com.stifflered.tcchallenge.tree.childtree.WorldTreeNodeManager;
import com.stifflered.tcchallenge.tree.components.ComponentStorage;
import com.stifflered.tcchallenge.tree.type.TreeType;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface Tree permits OfflineTree, OnlineTree {

    @NotNull
    TreeKey getKey();

    @NotNull
    TreeType getType();

    @NotNull
    ComponentStorage getComponentStorage();

    @NotNull
    PlayerProfile getOwner();

    @NotNull
    Location getHeart();

    @NotNull
    WorldTreeNodeManager getNodeManager();

    @Nullable
    String getName();

    @Nullable
    Location getSpawnLocation();

    void setSpawnLocation(@Nullable Location location);

    default void save() {
        TreeManager.INSTANCE.saveTree(this);
    }
}
