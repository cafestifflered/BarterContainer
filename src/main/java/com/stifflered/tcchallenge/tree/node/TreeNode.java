package com.stifflered.tcchallenge.tree.node;

import com.stifflered.tcchallenge.tree.OnlineTree;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class TreeNode {

    private final OnlineTree tree;
    private final Location min;
    private final Location max;

    private final Location location;

    public TreeNode(OnlineTree tree, Location location, int radius) {
        this.tree = tree;
        this.location = location;
        this.min = location.clone().subtract(radius, radius, radius);
        this.max = location.clone().add(radius, radius, radius);
    }

    @NotNull
    public Location getMin() {
        return this.min;
    }

    @NotNull
    public Location getMax() {
        return this.max;
    }

    public Location getLocation() {
        return this.location;
    }

    public OnlineTree getTree() {
        return this.tree;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;
        TreeNode treeNode = (TreeNode) o;
        return Objects.equals(this.location, treeNode.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.location);
    }
}
