package com.stifflered.tcchallenge.tree.root;

import com.stifflered.tcchallenge.tree.Tree;
import com.stifflered.tcchallenge.tree.components.child.node.TreeNode;
import com.stifflered.tcchallenge.tree.root.type.Root;
import com.stifflered.tcchallenge.util.Direction;
import com.stifflered.tcchallenge.util.storage.chunked.SimpleChunkedStorage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class RootManager {

    private static final Direction[] DIRECTIONS = Direction.values();

    private final SimpleChunkedStorage<Root> rootSimpleChunkedStorage = new SimpleChunkedStorage<>();

    @Nullable
    public Root getRoot(Location location) {
        return this.rootSimpleChunkedStorage.getData(location);
    }

    @Nullable
    public Root getRoot(int x, int y, int z) {
        return this.rootSimpleChunkedStorage.getData(x, y, z);
    }

    public void putRoot(int xCoord, int yCoord, int zCoord, Root root) {
        this.rootSimpleChunkedStorage.putData(xCoord, yCoord, zCoord, root);
    }

    public void putRoot(Location location, Root root) {
        this.putRoot(location.getBlockX(), location.getBlockY(), location.getBlockZ(), root);
    }

    public void removeRoot(Location location) {
        this.rootSimpleChunkedStorage.removeData(location);
    }


    public boolean hasAnyValidPathsToNode(Tree tree, Location start) {
        for (TreeNode treeNode : tree.getNodeManager().getNearbyNodesAt(start)) {
            if (treeNode.getLocation().equals(start)) {
                return true;
            }

            RootTraceResult result = this.traceRootPath(tree, start, treeNode.getLocation());
            if (result != null && result.reachedEnd.get()) {
                return true;
            }
        }

        return false;
    }

    public RootTraceResult traceRootPath(Tree key, Location start, Location target) {
        return this.traceRootPath(key, start, target, () -> {
            return new RootTraceResult(key, new HashSet<>(), new AtomicBoolean());
        });
    }

    public RootTraceResult traceRootPath(Tree tree, Location start, Location target, Supplier<RootTraceResult> resultSupplier) {
        World world = start.getWorld();
        Vector vector = start.toVector();
        Vector targetVector = target.toVector();
        for (Direction direction : DIRECTIONS) {
            direction.addToVector(vector);
            RootTraceResult stack = resultSupplier.get();
            try {
                this.trace(tree, world, vector, targetVector, stack);
            } catch (StackOverflowError error) {
                error.printStackTrace();
            }
            direction.subtractFromVector(vector);

            if (!stack.reachedEnd.get()) {
                stack.blocks.clear();
            } else {
                return stack;
            }
        }

        return null;
    }

    private void trace(Tree tree, World world, Vector start, Vector target, RootTraceResult stack) {
        if (!tree.getNodeManager().hasNodesAtLocation(world, start)) {
            stack.blocks.add(start.clone());
            return;
        }

        if (start.equals(target)) {
            stack.reachedEnd.set(true);
            stack.blocks.add(start.clone());
            return;
        }

        Root root = this.getRoot(start.getBlockX(), start.getBlockY(), start.getBlockZ());
        if (root != null && !stack.blocks.contains(start) && root.getKey().equals(stack.parent.getKey())) {
            stack.blocks.add(start.clone());

            for (Direction direction : DIRECTIONS) {
                direction.addToVector(start);
                this.trace(tree, world, start, target, stack);
                direction.subtractFromVector(start);
            }
        }
    }

    public record RootTraceResult(Tree parent, Set<Vector> blocks, AtomicBoolean reachedEnd) {

    }


    public SimpleChunkedStorage<Root> getStorage() {
        return this.rootSimpleChunkedStorage;
    }
}
