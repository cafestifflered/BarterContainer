package com.stifflered.tcchallenge.tree.root.type;

import com.stifflered.tcchallenge.tree.TreeKey;

public class MutableRoot implements Root {

    private final TreeKey tree;
    private boolean active;

    public MutableRoot(TreeKey tree) {
        this.tree = tree;
    }

    public TreeKey getTreeKey() {
        return this.tree;
    }

    @Override
    public TreeKey getKey() {
        return this.tree;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return "MutableRoot{" +
                "tree=" + this.tree +
                ", active=" + this.active +
                '}';
    }
}
