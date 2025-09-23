package com.stifflered.bartercontainer.item;

import com.stifflered.bartercontainer.item.impl.*;

/**
 * Central registry of concrete ItemInstance singletons.

 * Pattern:
 *  - Interface with public static final fields creates eager singletons at class load.
 *  - Each instance constructs and self-registers its PDC key via ItemInstance base class.

 * Members:
 *  - SHOP_LISTER_ITEM: tool/token to create a new barter shop.
 *  - SHOP_FIXER_ITEM: maintenance/migration tool (exact behavior defined in its impl).

 * Usage:
 *  - Access .getItem() to obtain the tagged ItemStack to give to players.
 *  - Use ItemInstance.fromStack(stack) during events to resolve which instance (if any) a stack belongs to.
 */
public interface ItemInstances {

    ShopListerItem SHOP_LISTER_ITEM = new ShopListerItem();
    ShopFixerItem SHOP_FIXER_ITEM = new ShopFixerItem();
}
