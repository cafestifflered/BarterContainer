package com.stifflered.bartercontainer.util;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public enum Direction {
    NORTH(0, 0, -1),
    EAST(1, 0, 0),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    UP(0, 1, 0),
    DOWN(0, -1, 0),
    ;

    private final int modX;
    private final int modY;
    private final int modZ;

    Direction(final int modX, final int modY, final int modZ) {
        this.modX = modX;
        this.modY = modY;
        this.modZ = modZ;
    }

    public void subtractFromVector(Vector vector) {
        vector.setX(vector.getX() - this.modX);
        vector.setY(vector.getY() - this.modY);
        vector.setZ(vector.getZ() - this.modZ);
    }

    public void addToVector(Vector vector) {
        vector.setX(vector.getX() + this.modX);
        vector.setY(vector.getY() + this.modY);
        vector.setZ(vector.getZ() + this.modZ);
    }

    public void addToLocation(Location location) {
        location.setX(location.getX() + this.modX);
        location.setY(location.getY() + this.modY);
        location.setZ(location.getZ() + this.modZ);
    }

    public void subtractFromLocation(Location location) {
        location.setX(location.getX() - this.modX);
        location.setY(location.getY() - this.modY);
        location.setZ(location.getZ() - this.modZ);
    }

    @NotNull
    public Direction getOpposite() {
        return switch (this) {
            case NORTH -> Direction.SOUTH;
            case SOUTH -> Direction.NORTH;
            case EAST -> Direction.WEST;
            case WEST -> Direction.EAST;
            case UP -> Direction.DOWN;
            case DOWN -> Direction.UP;
        };

    }
}
