package com.stifflered.bartercontainer.barter.serializers;

import com.stifflered.bartercontainer.barter.BarterStoreKeyImpl;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.store.BarterStoreKey;
import com.stifflered.bartercontainer.util.TagUtil;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Handles encoding and decoding of BarterStore identity into a block's PersistentDataContainer.

 * Purpose:
 *  - Acts as a "serializer" for the store key (UUID) so that a physical container block in the world
 *    (barrel, chest, etc.) can be permanently associated with a BarterStore in storage.

 * Key Design:
 *  - Uses a fixed NamespacedKey "barter_key" created via TagUtil.of().
 *  - Stored value type: STRING, containing a UUID in its textual form.
 *  - Provides read (getBarterStoreKey) and write (writeBarterStoreKey) methods.

 * Notes:
 *  - Only identity is handled here (UUID <-> BarterStoreKey). Full store serialization is handled elsewhere.
 *  - Optional is used to handle the case where no barter_key is present in the container.
 *  - This class could later be extended to serialize more store metadata if needed.
 */
public class InGameBarterSerializer {

    /** PDC key under which the store's UUID is stored on container blocks. */
    private static final NamespacedKey BARTER_KEY_TAG = TagUtil.of("barter_key");

    /**
     * Attempts to extract a BarterStoreKey from a block's PersistentDataContainer.

     * Flow:
     *  - container.get("barter_key", STRING) → string UUID (or null if absent).
     *  - Map to UUID.fromString(...) → UUID.
     *  - Wrap in BarterStoreKeyImpl.
     *
     * @return Optional.empty() if no key present or invalid; Optional.of(key) otherwise.
     */
    public Optional<BarterStoreKey> getBarterStoreKey(PersistentDataContainer container) {
        return Optional.ofNullable(container.get(BARTER_KEY_TAG, PersistentDataType.STRING))
                .map(UUID::fromString)
                .map(BarterStoreKeyImpl::new);
    }

    /**
     * Writes the BarterStoreKey into the given container block's PDC.

     * Behavior:
     *  - Stores the UUID from barterStore.getKey().key() as a STRING.
     *  - After this call, the block in-world is permanently linked to the logical BarterStore.

     * Pitfalls:
     *  - Overwrites any existing key if already present.
     *  - Caller must ensure this block is indeed a valid barter container.
     */
    public void writeBarterStoreKey(PersistentDataContainer container, BarterStore barterStore) {
        container.set(BARTER_KEY_TAG, PersistentDataType.STRING, barterStore.getKey().key().toString());
    }

}
