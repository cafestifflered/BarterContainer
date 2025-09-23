package com.stifflered.bartercontainer.util;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/**
 * Iterator for traversing the six block-adjacent neighboring locations
 * (north, east, south, west, up, and down) around a central {@link Location}.
 * <p>
 * Usage example:
 * <pre>{@code
 * Location center = player.getLocation();
 * for (Location neighbor : new NeighboringLocationIterator(center)) {
 *     // Do something with each adjacent block
 * }
 * }</pre>
 *
 * @see Direction
 */
public class NeighboringLocationIterator implements Iterator<Location>, Iterable<Location> {

    /** The six possible directions around a block (N, E, S, W, UP, DOWN). */
    private static final Direction[] DIRECTIONS = Direction.values();

    /** The central location whose neighbors are iterated. */
    private final Location main;

    /** Index of the next direction to evaluate. */
    private int currentDirection;

    /**
     * Creates a new iterator for locations adjacent to the given center.
     *
     * @param center the central {@link Location} to check neighbors of
     */
    public NeighboringLocationIterator(Location center) {
        this.main = center;
    }

    /**
     * Checks if there are more neighboring locations to iterate.
     *
     * @return true if another neighbor exists, false otherwise
     */
    @Override
    public boolean hasNext() {
        return DIRECTIONS.length > this.currentDirection;
    }

    /**
     * Gets the next neighboring {@link Location}, offset from the center
     * in the next {@link Direction}.
     *
     * @return the next adjacent location
     */
    @Override
    public Location next() {
        Location location = this.main.clone();
        Direction direction = DIRECTIONS[this.currentDirection++];
        direction.addToLocation(location);
        return location;
    }

    /**
     * Allows this class to be used directly in enhanced-for loops.
     *
     * @return this instance as an {@link Iterator}
     */
    @NotNull
    @Override
    public Iterator<Location> iterator() {
        return this;
    }
}
