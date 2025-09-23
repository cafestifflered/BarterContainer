package com.stifflered.bartercontainer.util;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Discrete cardinal + vertical directions with integer step modifiers.

 * Each enum constant carries (modX, modY, modZ) offsets that represent a
 * single-step movement in that direction, suitable for block/grid operations.

 * Convenience methods mutate a given {@link Vector} or {@link Location}
 * in-place by adding or subtracting these offsets.
 */
public enum Direction {
    // Horizontal cardinals (XZ plane)
    NORTH(0, 0, -1),
    EAST(1, 0, 0),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),

    // Vertical directions (Y axis)
    UP(0, 1, 0),
    DOWN(0, -1, 0),
    ;

    /** Unit step along X axis for this direction (can be -1, 0, or 1). */
    private final int modX;
    /** Unit step along Y axis for this direction (can be -1, 0, or 1). */
    private final int modY;
    /** Unit step along Z axis for this direction (can be -1, 0, or 1). */
    private final int modZ;

    Direction(final int modX, final int modY, final int modZ) {
        this.modX = modX;
        this.modY = modY;
        this.modZ = modZ;
    }

    /**
     * In-place: v -= directionDelta
     * Subtract this direction's step from the given vector.
     */
    public void subtractFromVector(Vector vector) {
        vector.setX(vector.getX() - this.modX);
        vector.setY(vector.getY() - this.modY);
        vector.setZ(vector.getZ() - this.modZ);
    }

    /**
     * In-place: v += directionDelta
     * Add this direction's step to the given vector.
     */
    public void addToVector(Vector vector) {
        vector.setX(vector.getX() + this.modX);
        vector.setY(vector.getY() + this.modY);
        vector.setZ(vector.getZ() + this.modZ);
    }

    /**
     * In-place: loc += directionDelta
     * Add this direction's step to the given location's coordinates.
     * (World reference is untouched.)
     */
    public void addToLocation(Location location) {
        location.setX(location.getX() + this.modX);
        location.setY(location.getY() + this.modY);
        location.setZ(location.getZ() + this.modZ);
    }

    /**
     * In-place: loc -= directionDelta
     * Subtract this direction's step from the given location's coordinates.
     * (World reference is untouched.)
     */
    public void subtractFromLocation(Location location) {
        location.setX(location.getX() - this.modX);
        location.setY(location.getY() - this.modY);
        location.setZ(location.getZ() - this.modZ);
    }

    /**
     * Returns the opposite cardinal/vertical direction.
     * NORTH ↔ SOUTH, EAST ↔ WEST, UP ↔ DOWN.
     */
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
