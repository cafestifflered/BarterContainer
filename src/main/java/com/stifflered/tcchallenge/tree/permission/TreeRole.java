package com.stifflered.tcchallenge.tree.permission;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum TreeRole {
    VISITOR(TreePermission.CONNECT_TO_ROOTS),
    PLANTER(TreePermission.CONNECT_TO_ROOTS, TreePermission.ADD_ROOTS),
    ADMIN(TreePermission.CONNECT_TO_ROOTS, TreePermission.ADD_ROOTS, TreePermission.MANAGE_MEMBERS),
    ;
    private final Set<TreePermission> treePermissions;

    TreeRole(TreePermission... permissionsArray) {
        this.treePermissions = EnumSet.noneOf(TreePermission.class);
        Collections.addAll(this.treePermissions, permissionsArray);
    }

    public boolean hasPermission(@NotNull TreePermission permission) {
        return this.treePermissions.contains(permission);
    }
}
