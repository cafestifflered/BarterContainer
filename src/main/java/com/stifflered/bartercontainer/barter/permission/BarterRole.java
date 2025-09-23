package com.stifflered.bartercontainer.barter.permission;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Enum representing user roles within a barter store.

 * Roles define which {@link BarterPermission}s a player has when interacting with a shop.

 * Current roles:
 *  - VISITOR: default role for non-owners; can only perform trades (BARTER).
 *  - UPKEEPER: full-control role; has all permissions (DELETE, EDIT_PRICE, etc.).

 * Implementation details:
 *  - Each role is constructed with a set of permissions (provided as varargs).
 *  - Internally stored in an EnumSet for fast membership checks.

 * Notes:
 *  - Role assignment logic is handled in {@link com.stifflered.bartercontainer.store.BarterStoreImpl#getRole}.
 *  - Permissions are queried via hasPermission(...).
 *  - More roles could be added in the future (e.g., MANAGER, CO_OWNER).
 */
public enum BarterRole {
    /** Default player role: may only perform barter trades. */
    VISITOR(BarterPermission.BARTER),

    /** Full-access role: may edit, redeem, delete, etc. */
    UPKEEPER(BarterPermission.values()),
    ;

    /** Permissions granted to this role. */
    private final Set<BarterPermission> treePermissions;

    BarterRole(BarterPermission... permissionsArray) {
        this.treePermissions = EnumSet.noneOf(BarterPermission.class);
        Collections.addAll(this.treePermissions, permissionsArray);
    }

    /** Check if this role grants the specified permission. */
    public boolean hasPermission(@NotNull BarterPermission permission) {
        return this.treePermissions.contains(permission);
    }
}
