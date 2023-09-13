package com.stifflered.tcchallenge.tree;

import com.stifflered.tcchallenge.util.WeakStorage;
import com.stifflered.tcchallenge.util.source.ObjectSource;
import com.stifflered.tcchallenge.util.source.Sources;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

public class TreeStorageManager {

    private static final ObjectSource<TreeKey, Tree> TREE_SOURCE = Sources.TREE_SOURCE;

    private final WeakStorage<TreeKey, Tree> treeStorage = new WeakStorage<>(TimeUnit.MINUTES.toMillis(1));

    @Nullable
    public OnlineTree upgradeTree(TreeKey tree) throws IllegalStateException {
        try {
            Tree access = this.treeStorage.get(tree);
            // Recreate tree, even if online
            if (access instanceof OfflineTree offlineTree) {
                OnlineTree onlineTree = OnlineTree.upgrade(offlineTree);
                this.treeStorage.put(tree, onlineTree);
                TreeManager.INSTANCE.registerTree(onlineTree);
                return onlineTree;
            }

            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load %s's tree!".formatted(tree), e);
        }

    }

    public void downgradeTree(OnlineTree tree) {
        this.treeStorage.put(tree.key, new OfflineTree(tree.key, tree.owner, tree.name, tree.heart, tree.respawn, tree.type, tree.storage));
        tree.onUnregister();
    }

    @Nullable
    public Tree loadTreeBlocking(TreeKey tree) {
        try {
            if (this.treeStorage.isStored(tree)) {
                return this.treeStorage.get(tree);
            }

            Tree offlineTree = TREE_SOURCE.load(tree);

            if (offlineTree != null) {
                this.treeStorage.put(tree, offlineTree);
            }

            return offlineTree;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load %s's tree!".formatted(tree), e);
        }
    }

    @Nullable
    public OnlineTree getTreeIfLoaded(TreeKey tree) {
        return this.treeStorage.get(tree) instanceof OnlineTree onlineTree ? onlineTree : null;
    }

    public void registerTree(OnlineTree tree) {
        this.treeStorage.put(tree.getKey(), tree);
    }

    public void unloadTree(OnlineTree tree) {
        this.treeStorage.remove(tree.getKey());
    }

    public Collection<Tree> getLoadedTrees() {
        return this.treeStorage.values();
    }
}
