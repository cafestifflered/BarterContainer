package com.stifflered.tcchallenge.tree;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.stifflered.tcchallenge.tree.childtree.TreeNodeManager;
import com.stifflered.tcchallenge.tree.childtree.WorldTreeNodeManager;
import com.stifflered.tcchallenge.tree.components.ComponentStorage;
import com.stifflered.tcchallenge.tree.type.TreeType;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public non-sealed class OfflineTree implements Tree {

    protected final TreeKey key;

    protected final ComponentStorage storage;
    protected final PlayerProfile owner;
    protected final Location heart;
    protected final TreeType type;
    protected final WorldTreeNodeManager childTreeManager;
    @Nullable
    protected String name = null;
    @Nullable
    protected Location respawn;

    public OfflineTree(TreeKey key, PlayerProfile owner, @Nullable String name, Location heart, @Nullable Location respawn, TreeType type, ComponentStorage storage) {
        this.key = key;
        this.storage = storage;
        this.heart = heart;
        this.respawn = respawn;
        this.owner = owner;
        this.type = type;
        this.name = name;
        this.childTreeManager = new WorldTreeNodeManager(this);
    }

    @Override
    public @NotNull TreeKey getKey() {
        return this.key;
    }

    @Override
    public @NotNull TreeType getType() {
        return this.type;
    }

    @Override
    public @NotNull ComponentStorage getComponentStorage() {
        return this.storage;
    }

    @Override
    public @NotNull PlayerProfile getOwner() {
        return this.owner;
    }

    @Override
    public @NotNull Location getHeart() {
        return this.heart.clone();
    }

    @Override
    public @NotNull WorldTreeNodeManager getNodeManager() {
        return this.childTreeManager;
    }

    @Override
    public @Nullable String getName() {
        return this.name;
    }

    @Override
    public @Nullable Location getSpawnLocation() {
        return this.respawn == null ? null : this.respawn.clone();
    }

    public void setSpawnLocation(@Nullable Location respawn) {
        this.respawn = respawn;
    }

    public void saveChanges() {
        TreeManager.INSTANCE.saveTree(this);
    }
}
