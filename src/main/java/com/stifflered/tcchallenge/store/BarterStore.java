package com.stifflered.tcchallenge.store;

/**
 * Lets store in the bartering object:
 * - owner (player profile)
 * - time created
 * - bartering item
 */
public interface BarterStore {

    BarterStoreKey getKey();
}
