package com.stifflered.tcchallenge.tree;

import com.stifflered.tcchallenge.BarterChallenge;
import com.stifflered.tcchallenge.util.source.ObjectSource;
import com.stifflered.tcchallenge.util.source.Sources;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class TreeManager {

    public static final TreeManager INSTANCE = new TreeManager();

    private static final ObjectSource<TreeKey, Tree> TREE_SOURCE = Sources.TREE_SOURCE;
    private final TreeStorageManager storageManager = new TreeStorageManager();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public TreeStorageManager getStorageManager() {
        return this.storageManager;
    }

    public CompletableFuture<@Nullable Tree> getTree(TreeKey key) {
        OnlineTree onlineTree = this.storageManager.getTreeIfLoaded(key);;
        if (onlineTree != null) {
            return CompletableFuture.completedFuture(onlineTree);
        }

        return this.completeAsyncToMainThread(() -> {
            return this.storageManager.loadTreeBlocking(key);
        });
    }


    public void saveTree(@NotNull Tree tree) {
        this.executor.execute(() -> {
            try {
                TREE_SOURCE.save(tree);
            } catch (Exception e) {
                throw new RuntimeException("Failed to save %s's tree!".formatted(tree.getOwner().getId()), e);
            }
        });
    }

    public void deleteTree(OnlineTree tree) {
        this.unloadTree(tree, false);
        this.executor.execute(() -> {
            try {
                TREE_SOURCE.delete(tree);
            } catch (Exception e) {
                throw new RuntimeException("Failed to delete %s's tree!".formatted(tree.getOwner().getId()), e);
            }
        });
    }

    @Nullable
    public OnlineTree getTreeIfLoaded(TreeKey treeKey) {
        return this.getStorageManager().getTreeIfLoaded(treeKey);
    }

    @Nullable
    public OnlineTree getTreeIfLoaded(Player player) {
        return this.getStorageManager().getTreeIfLoaded(new TreeKey(player.getUniqueId()));
    }

    @NotNull
    private <T> CompletableFuture<T> completeAsyncToMainThread(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();

        this.executor.execute(() -> {
            T object;
            try {
                object = supplier.get();
            } catch (Exception e) {
                future.completeExceptionally(e);
                return;
            }

            new BukkitRunnable() {

                @Override
                public void run() {
                    future.complete(object);
                }
            }.runTask(BarterChallenge.INSTANCE);
        });

        return future;
    }

    public void unloadTree(OnlineTree tree, boolean save) {
        if (save) {
            this.saveTree(tree);
        }
        this.storageManager.unloadTree(tree);
        tree.onUnregister();
    }

    public void registerTree(OnlineTree tree) {
        this.storageManager.registerTree(tree);
        tree.onRegister();
    }

    public Collection<Tree> getLoadedTrees() {
        return this.storageManager.getLoadedTrees();
    }

    public ExecutorService getExecutor() {
        return this.executor;
    }
}
