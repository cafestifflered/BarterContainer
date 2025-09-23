package com.stifflered.bartercontainer.store;

import java.util.UUID;

/**
 * Represents the stable, unique identifier for a {@link BarterStore}.
 *
 * <p>This abstraction lets other components (persistence, logs, analytics) refer to
 * a store by its key rather than holding a direct object reference. That keeps
 * lookups simple and robust across reloads, server restarts, or lazy loading.</p>
 *
 * <h3>Where this is used</h3>
 * <ul>
 *   <li>Transaction logs: {@code BarterShopOwnerLogManager.listAllEntries(key)}</li>
 *   <li>Analytics: weekly aggregation (e.g., {@code WeeklyConsistencySnapshot}) consumes
 *       a collection of keys per owner to compute all-barrels consistency.</li>
 * </ul>
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>Implementations must be <strong>immutable</strong>.</li>
 *   <li>{@link #key()} must uniquely and stably identify a store across restarts.</li>
 *   <li>{@code equals}, {@code hashCode}, and {@code toString} should delegate to {@link #key()}.</li>
 * </ul>
 *
 * <p>Implementation detail:
 * • Currently {@code com.stifflered.bartercontainer.barter.BarterStoreKeyImpl} is a concrete
 *   record that wraps a {@link UUID}. Keeping this as an interface preserves flexibility if store
 *   identifiers ever need to change (e.g., composite IDs).</p>
 */
public interface BarterStoreKey {

    /**
     * @return the underlying UUID that uniquely identifies this barter store.
     */
    UUID key();
}
