package com.stifflered.bartercontainer.util.source;

import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.store.BarterStoreKey;
import com.stifflered.bartercontainer.util.source.impl.BarterStorage;

public interface Sources {

    ObjectSource<BarterStoreKey, BarterStore> BARTER_STORAGE = new BarterStorage();

}
