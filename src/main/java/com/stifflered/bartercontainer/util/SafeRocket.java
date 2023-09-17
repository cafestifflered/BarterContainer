package com.stifflered.bartercontainer.util;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.function.Consumer;

public class SafeRocket {

    private static final NamespacedKey SAFE_ROCKET_KEY = TagUtil.of("safe_rocket");

    public static void instantRocket(Location location, Consumer<FireworkMeta> meta) {
        Firework firework = location.getWorld().spawn(location, Firework.class);
        FireworkMeta fireworkMeta = firework.getFireworkMeta();
        meta.accept(fireworkMeta);

        firework.setFireworkMeta(fireworkMeta);
        firework.detonate();
        TagUtil.setIntegerTag(firework, SAFE_ROCKET_KEY, 0);
    }

    public static boolean isSafeRocket(Firework firework) {
        return TagUtil.hasTag(firework, SAFE_ROCKET_KEY);
    }
}
