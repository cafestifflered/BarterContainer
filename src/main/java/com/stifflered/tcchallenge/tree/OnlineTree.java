package com.stifflered.tcchallenge.tree;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.stifflered.tcchallenge.tree.blocks.HeartBlock;
import com.stifflered.tcchallenge.tree.blocks.RespawnBlock;
import com.stifflered.tcchallenge.tree.blocks.lookup.local.LocalBlockLookupStorage;
import com.stifflered.tcchallenge.tree.blocks.lookup.local.LocalImportantBlockLookup;
import com.stifflered.tcchallenge.tree.childtree.SimplePrTreeNodeManager;
import com.stifflered.tcchallenge.tree.childtree.WorldTreeNodeManager;
import com.stifflered.tcchallenge.tree.components.ComponentStorage;
import com.stifflered.tcchallenge.tree.components.TreeComponent;
import com.stifflered.tcchallenge.tree.type.TreeType;
import com.stifflered.tcchallenge.util.StringUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class OnlineTree extends OfflineTree implements Tree {

    private final LocalImportantBlockLookup importantBlockManager;
    private boolean isNewTree = false;
    @Nullable
    private RespawnBlock respawnBlock = null;

    // tree upgrade constructor
    OnlineTree(TreeKey key, PlayerProfile owner, @Nullable String name, Location heart, @Nullable Location respawn, TreeType type, ComponentStorage storage) {
        super(key, owner, name, heart, respawn, type, storage);
        this.importantBlockManager = LocalBlockLookupStorage.INSTANCE.getOrCreate(key);
        this.importantBlockManager.setOwned(this);
    }

    // New tree constructor
    public OnlineTree(PlayerProfile owner, Location heart, TreeType type) {
        this(new TreeKey(owner.getId()), owner, null, heart, null, type, newStorage());
        this.isNewTree = true;
    }

    public static OnlineTree upgrade(OfflineTree offlineTree) {
        return new OnlineTree(offlineTree.key, offlineTree.owner, offlineTree.name, offlineTree.heart, offlineTree.respawn, offlineTree.type, offlineTree.storage);
    }

    private static ComponentStorage newStorage() {
        ComponentStorage componentStorage = new ComponentStorage();
        componentStorage.register(TreeComponent.MEMBER);
        componentStorage.register(TreeComponent.STATS);
        componentStorage.register(TreeComponent.CHILDREN);
        componentStorage.register(TreeComponent.COSMETIC);
        return componentStorage;
    }

    public Component getNameStyled() {
        return Component.text(Objects.requireNonNullElseGet(this.name, () -> StringUtil.smartPossessive(this.owner.getName()) + " Tree"));
    }

    @Nullable
    public String getRawName() {
        return this.name;
    }

    public void setRawName(String name) {
        this.name = name;
    }

    public void onRegister() {
        if (this.isNewTree) {
            HeartBlock heartBlock = new HeartBlock(this.getKey(), this.heart);
            this.importantBlockManager.addBlock(heartBlock);
            heartBlock.place();
        }
    }

    public void onUnregister() {
        this.importantBlockManager.setOwned(null);
        LocalBlockLookupStorage.INSTANCE.throwAwayLookupIfNeeded(this.key);
    }

    public void setSpawnBlock(Location location) {
        if (this.respawnBlock != null) {
            this.respawnBlock.invalidate();
            this.respawnBlock = null;
            this.respawn = null;
        }
        if (location != null) {
            this.respawnBlock = new RespawnBlock(this.key, location);
            this.importantBlockManager.addBlock(this.respawnBlock);
            this.respawn = location;
        }
    }

    // Blocks may be unloaded in the future
    @Nullable
    public HeartBlock getHeartblock() {
        if (this.importantBlockManager.getSpecialBlockFromLocation(this.heart) instanceof HeartBlock heartBlock) {
            return heartBlock;
        }

        return null;
    }

    public LocalImportantBlockLookup getImportantBlockManager() {
        return this.importantBlockManager;
    }

}
