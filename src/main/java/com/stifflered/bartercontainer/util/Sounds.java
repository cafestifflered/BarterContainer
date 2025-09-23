package com.stifflered.bartercontainer.util;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.sound.Sound;

/**
 * Utility class for playing common sound effects within the BarterContainer plugin.
 * <p>
 * These sounds use {@link Audience#playSound(Sound, Sound.Emitter)} to ensure they
 * play only to the target player/audience that triggered the event.
 * <p>
 * Each static method corresponds to a single in-game event (error, purchase, chest opening, etc.).
 */
public class Sounds {

    /** Played when opening a barter chest GUI. */
    private static final Sound OPEN_CHEST = Sound.sound(org.bukkit.Sound.BLOCK_CHEST_OPEN, Sound.Source.PLAYER, 1, 1.3F);

    /** Played when entering text (e.g., renaming or configuring shops). */
    private static final Sound WRITE_TEXT = Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, Sound.Source.PLAYER, 1, 2);

    /** Played when making a selection in a GUI (confirmation/click). */
    private static final Sound CHOOSE = Sound.sound(org.bukkit.Sound.BLOCK_STONE_BUTTON_CLICK_OFF, Sound.Source.PLAYER, 1, 2);

    /** Played when completing a successful purchase transaction. */
    private static final Sound PURCHASE = Sound.sound(org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, Sound.Source.PLAYER, 1, 2);

    /** Played when an error occurs (e.g., invalid action or denied permission). */
    private static final Sound ERROR = Sound.sound(org.bukkit.Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, Sound.Source.PLAYER, 1, 2);

    /**
     * Play the "write text" sound to the given audience.
     *
     * @param audience the audience (usually a {@link org.bukkit.entity.Player}) to hear the sound
     */
    public static void writeText(Audience audience) {
        audience.playSound(WRITE_TEXT, Sound.Emitter.self());
    }

    /**
     * Play the "choose/select" sound to the given audience.
     *
     * @param audience the audience to hear the sound
     */
    public static void choose(Audience audience) {
        audience.playSound(CHOOSE, Sound.Emitter.self());
    }

    /**
     * Play the "error" sound to the given audience.
     *
     * @param audience the audience to hear the sound
     */
    public static void error(Audience audience) {
        audience.playSound(ERROR, Sound.Emitter.self());
    }

    /**
     * Play the "open chest" sound to the given audience.
     *
     * @param audience the audience to hear the sound
     */
    public static void openChest(Audience audience) {
        audience.playSound(OPEN_CHEST, Sound.Emitter.self());
    }

    /**
     * Play the "purchase success" sound to the given audience.
     *
     * @param audience the audience to hear the sound
     */
    public static void purchase(Audience audience) {
        audience.playSound(PURCHASE, Sound.Emitter.self());
    }
}
