package com.stifflered.bartercontainer.util;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

public class NeighboringLocationIterator implements Iterator<Location>, Iterable<Location> {

    private static final Direction[] DIRECTIONS = Direction.values();
    private final Location main;
    private int currentDirection;

    public NeighboringLocationIterator(Location center) {
        this.main = center;
    }

    @Override
    public boolean hasNext() {
        return DIRECTIONS.length > this.currentDirection;
    }

    @Override
    public Location next() {
        Location location = this.main.clone();
        Direction direction = DIRECTIONS[this.currentDirection++];
        direction.addToLocation(location);

        return location;
    }

    @NotNull
    @Override
    public Iterator<Location> iterator() {
        return this;
    }
}
