package com.stifflered.bartercontainer.barter.permission;

/**
 * Enum representing the different actions that can be granted/checked
 * within the barter container system.

 * Each constant corresponds to a capability that a role (see {@link BarterRole})
 * may or may not possess.

 * Current permissions:
 *  - BARTER: ability to perform trades with the shop.
 *  - EDIT_PRICE: ability to change the barter price item.
 *  - REDEEM_ITEMS: ability to withdraw collected currency/revenue.
 *  - EDIT_ITEMS: ability to add/remove items from the sale inventory.
 *  - DELETE: ability to dismantle/remove the shop.

 * Notes:
 *  - Permission checks are performed via BarterRole#hasPermission(BarterPermission).
 *  - Roles are assigned by BarterStoreImpl#getRole(Player).
 */
public enum BarterPermission {
    BARTER,
    EDIT_PRICE,
    REDEEM_ITEMS,
    EDIT_ITEMS,
    DELETE
}
