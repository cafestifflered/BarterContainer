package com.stifflered.bartercontainer.util.source.codec;

import com.google.gson.*;
import org.bukkit.*;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
import com.stifflered.bartercontainer.util.Messages;

/**
 * Codec to (de)serialize a Bukkit {@link Location} at BLOCK precision to/from a JSON object.

 * Design:
 *  - Writes integer block coordinates (x,y,z) instead of doubles.
 *  - Stores world by its {@link NamespacedKey} string (e.g., "minecraft:overworld" or custom worlds).

 * Pitfalls:
 *  - decode(...) throws if the world cannot be found on this server instance.
 *  - Only block coords are preserved; any fractional position or yaw/pitch is discarded by intent.
 */
public class BlockLocationSerializer implements Codec<Location, JsonObject> {

    /** Reusable singleton for consumers that want a shared instance. */
    public static final BlockLocationSerializer INSTANCE = new BlockLocationSerializer();

    /** Convert a Location to a compact JSON object with block coords and world key. */
    @Override
    public JsonObject encode(Location from) {
        JsonObject object = new JsonObject();
        object.addProperty("x", from.getBlockX());
        object.addProperty("y", from.getBlockY());
        object.addProperty("z", from.getBlockZ());
        object.addProperty("world", from.getWorld().getKey().toString());
        return object;
    }

    /**
     * Recreate a Location from its JSON representation.
     * Expects:
     *   { "x": int, "y": int, "z": int, "world": "namespace:id" }

     * Throws:
     *  - IllegalArgumentException if the world is not loaded or not present on this server.
     */
    @Override
    public Location decode(JsonObject type) {
        String worldStr = type.get("world").getAsString();
        NamespacedKey key = NamespacedKey.fromString(worldStr);
        if (key == null) {
            throw new IllegalArgumentException(Messages.fmt(
                    "codec.block_location.bad_world_key",
                    "world", worldStr
            ));
        }

        World world = Bukkit.getWorld(key);
        if (world == null) {
            throw new IllegalArgumentException(Messages.fmt(
                    "codec.block_location.world_missing",
                    "world", key.toString()
            ));
        }

        int x = type.get("x").getAsInt();
        int y = type.get("y").getAsInt();
        int z = type.get("z").getAsInt();

        return new Location(world, x, y, z);
    }
}
