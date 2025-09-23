package com.stifflered.bartercontainer.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.intellij.lang.annotations.Subst;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Messages utility:
 *  - Loads src/main/resources/messages.yml → plugins/<YourPlugin>/messages.yml (copies default if missing).
 *  - Uses MiniMessage so colors, hex, gradients, decorations, etc. live in YAML (not hard-coded).

 * Placeholder usage:
 *  - mm(path, "key","value", ...)                   → values are UNPARSED (treated as plain text)
 *  - mmParsed(path, "key","<bold>value</bold>", ...)→ values are PARSED as MiniMessage
 *  - mm(path, "key", Component)                     → component placeholder

 * Notes:
 *  - Prefer Messages.mm(...) anywhere you used ChatColor/NamedTextColor concatenation.
 *  - Keep all visual styling in messages.yml.
 *  - IDE friendliness:
 *      To silence IntelliJ’s “Unsubstituted expression” when passing dynamic placeholder names,
 *      we route calls through small wrappers whose parameter is annotated with @Subst("key").
 *      This keeps warnings out of your core code without changing runtime behavior.
 */
public class Messages {

    private static JavaPlugin plugin;
    private static FileConfiguration messagesConfig;
    private static final Map<String, String> cache = new HashMap<>();
    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Initialize once in onEnable(): Messages.init(this); */
    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        reload();
    }

    /** Reload messages.yml (copies default from JAR if absent). */
    public static void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(file);

        // Defaults from inside the JAR
        try (InputStream defStream = plugin.getResource("messages.yml")) {
            if (defStream != null) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defStream, StandardCharsets.UTF_8));
                messagesConfig.setDefaults(defConfig);
            }
        } catch (Exception ignored) {
        }

        cache.clear();
    }

    /** Raw string fetch (used internally). Never null; falls back to the path key itself. */
    public static String get(String path) {
        if (cache.containsKey(path)) return cache.get(path);
        String value = messagesConfig.getString(path, path);
        cache.put(path, value);
        return value;
    }

    /* --------------------------------------------------------------------
     * MiniMessage helpers
     * -------------------------------------------------------------------- */

    /**
     * Build a TagResolver from alternating key/value varargs.
     * Keys must be String; values may be String or Component.
     * String values are UNPARSED by default to avoid MiniMessage injection from dynamic data.

     * Odd trailing key without value is ignored.
     */
    private static TagResolver tagsUnparsed(Object... placeholders) {
        if (placeholders == null || placeholders.length == 0) return TagResolver.empty();
        List<TagResolver> list = new ArrayList<>(placeholders.length / 2);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            Object k = placeholders[i];
            Object v = placeholders[i + 1];
            if (!(k instanceof String key)) continue;

            if (v instanceof Component comp) {
                list.add(phComponent(key, comp));
            } else if (v instanceof String s) {
                // UNPARSED by default to avoid MM injection from dynamic values
                list.add(phUnparsed(key, s));
            } else if (v != null) {
                list.add(phUnparsed(key, String.valueOf(v)));
            }
        }
        return TagResolver.resolver(list);
    }

    /**
     * Same as tagsUnparsed but PARSES String values as MiniMessage.
     * Use when you explicitly want rich formatting inside the values.
     */
    private static TagResolver tagsParsed(Object... placeholders) {
        if (placeholders == null || placeholders.length == 0) return TagResolver.empty();
        List<TagResolver> list = new ArrayList<>(placeholders.length / 2);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            Object k = placeholders[i];
            Object v = placeholders[i + 1];
            if (!(k instanceof String key)) continue;

            if (v instanceof Component comp) {
                list.add(phComponent(key, comp));
            } else if (v instanceof String s) {
                list.add(phParsed(key, s)); // allow rich formatting in values
            } else if (v != null) {
                list.add(phUnparsed(key, String.valueOf(v)));
            }
        }
        return TagResolver.resolver(list);
    }

    /** Deserialize a Component from messages.yml using UNPARSED placeholder substitution. */
    public static Component mm(String path, Object... placeholders) {
        String raw = get(path);
        return MM.deserialize(raw, tagsUnparsed(placeholders));
    }

    /** Deserialize a Component from messages.yml using PARSED placeholder substitution. */
    public static Component mmParsed(String path, Object... placeholders) {
        String raw = get(path);
        return MM.deserialize(raw, tagsParsed(placeholders));
    }

    /** Convenience: convert any Component to plain text (e.g., for logs). */
    public static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /**
     * Convenience for logging: resolve placeholders (UNPARSED) from messages.yml and strip styling to a plain String.
     * Example:
     *   logger.warning(Messages.fmt("barter.manager.missing_storage", "key", key));
     */
    public static String fmt(String path, Object... placeholders) {
        return plain(mm(path, placeholders));
    }

    /* --------------------------------------------------------------------
     * Send helpers
     * -------------------------------------------------------------------- */

    /** Send a MiniMessage-styled message to a player (unparsed placeholders). */
    public static void send(Player player, String path, Object... placeholders) {
        player.sendMessage(mm(path, placeholders));
    }

    /** Send a MiniMessage-styled message with parsed placeholders. */
    public static void sendParsed(Player player, String path, Object... placeholders) {
        player.sendMessage(mmParsed(path, placeholders));
    }

    /**
     * Send an error message to the player and play error sound.
     * You can style the message in messages.yml (e.g., red, bold, gradients).
     */
    public static void error(Player player, String path, Object... placeholders) {
        player.sendMessage(mm(path, placeholders));
        Sounds.error(player);
    }

    /**
     * Back-compatible helper if you already have a built Component.
     * Still plays the error sound.
     */
    public static void error(Player player, Component message) {
        player.sendMessage(message);
        Sounds.error(player);
    }

    /**
     * Fetch a list of MiniMessage strings from messages.yml and deserialize each line with placeholders.
     * Useful for lore blocks, multi-line messages, etc.
     */
    public static List<Component> mmList(String path, Object... placeholders) {
        List<String> lines = messagesConfig.getStringList(path);
        if (lines.isEmpty()) return List.of();
        TagResolver tags = tagsUnparsed(placeholders);
        List<Component> out = new ArrayList<>(lines.size());
        for (String s : lines) {
            out.add(MM.deserialize(s, tags));
        }
        return out;
    }

    /* --------------------------------------------------------------------
     * Internal: IDE-friendly wrappers for Placeholder.*
     * These concentrate the @Subst usage in one place.
     * -------------------------------------------------------------------- */

    private static TagResolver phComponent(@Subst("key") String name, Component c) {
        return Placeholder.component(name, c);
    }

    private static TagResolver phParsed(@Subst("key") String name, String s) {
        return Placeholder.parsed(name, s);
    }

    private static TagResolver phUnparsed(@Subst("key") String name, String s) {
        return Placeholder.unparsed(name, s);
    }
}
