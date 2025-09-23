package com.stifflered.bartercontainer.util.source;

import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.store.BarterStoreKey;
import com.stifflered.bartercontainer.util.source.impl.BarterStorage;

/**
 * Central registry of object sources used by the plugin.

 * Current members:
 *  - BARTER_STORAGE: file-backed store for BarterStore objects keyed by BarterStoreKey.

 * Notes:
 *  - Declared as an interface with public static final fields (Java idiom for singletons).
 *  - The instance is eagerly created; first access will construct BarterStorage (ensuring directory exists).
 */
public interface Sources {

    /** File-based storage for BarterStores (located under <plugin>/barter_storage). */
    ObjectSource<BarterStoreKey, BarterStore> BARTER_STORAGE = new BarterStorage();

}
