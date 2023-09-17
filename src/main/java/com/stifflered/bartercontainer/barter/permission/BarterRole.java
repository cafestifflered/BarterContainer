package com.stifflered.bartercontainer.barter.permission;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum BarterRole {
    VISITOR(BarterPermission.BARTER),
    UPKEEPER(BarterPermission.values()),
    ;
    private final Set<BarterPermission> treePermissions;

    BarterRole(BarterPermission... permissionsArray) {
        this.treePermissions = EnumSet.noneOf(BarterPermission.class);
        Collections.addAll(this.treePermissions, permissionsArray);
    }

    public boolean hasPermission(@NotNull BarterPermission permission) {
        return this.treePermissions.contains(permission);
    }
}
