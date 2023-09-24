package com.stifflered.bartercontainer.util;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.sound.Sound;

public class Sounds {

    private static final Sound OPEN_CHEST = Sound.sound(org.bukkit.Sound.BLOCK_CHEST_OPEN, Sound.Source.PLAYER, 1, 1.3F);
    private static final Sound WRITE_TEXT = Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, Sound.Source.PLAYER, 1, 2);
    private static final Sound CHOOSE = Sound.sound(org.bukkit.Sound.BLOCK_STONE_BUTTON_CLICK_OFF, Sound.Source.PLAYER, 1, 2);
    private static final Sound PURCHASE = Sound.sound(org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, Sound.Source.PLAYER, 1, 2);
    private static final Sound ERROR = Sound.sound(org.bukkit.Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, Sound.Source.PLAYER, 1, 2);

    public static void writeText(Audience audience) {
        audience.playSound(WRITE_TEXT, Sound.Emitter.self());
    }

    public static void choose(Audience audience) {
        audience.playSound(CHOOSE, Sound.Emitter.self());
    }

    public static void error(Audience audience) {
        audience.playSound(ERROR, Sound.Emitter.self());
    }

    public static void openChest(Audience audience) {
        audience.playSound(OPEN_CHEST, Sound.Emitter.self());
    }

    public static void purchase(Audience audience) {
        audience.playSound(PURCHASE, Sound.Emitter.self());
    }
}
