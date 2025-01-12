package com.stifflered.bartercontainer.util.source.codec;

import com.google.gson.*;
import org.bukkit.*;

public class BlockLocationSerializer implements Codec<Location, JsonObject> {

    public static final BlockLocationSerializer INSTANCE = new BlockLocationSerializer();

    @Override
    public JsonObject encode(Location from) {
        JsonObject object = new JsonObject();
        object.addProperty("x", from.getBlockX());
        object.addProperty("y", from.getBlockY());
        object.addProperty("z", from.getBlockZ());
        object.addProperty("world", from.getWorld().getKey().toString());
        return object;
    }

    @Override
    public Location decode(JsonObject type) {
        NamespacedKey key = NamespacedKey.fromString(type.get("world").getAsString());
        World world = Bukkit.getWorld(key);
        if (world == null) {
            throw new IllegalArgumentException("Could not find world %s".formatted(key));
        }
        int x = type.get("x").getAsInt();
        int y = type.get("y").getAsInt();
        int z = type.get("z").getAsInt();

        return new Location(world, x, y, z);
    }
}
