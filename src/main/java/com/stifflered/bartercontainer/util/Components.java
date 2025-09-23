package com.stifflered.bartercontainer.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility helpers for building Adventure Components and writing them onto item meta.

 * Highlights:
 *  - PREFIX + prefixed* helpers: consistent plugin-styled messaging (info/error/success).
 *  - name(...) and lore(...): convenience methods that also disable italics by default.
 *  - mini / miniSplit: MiniMessage deserialization shortcuts with optional resolvers.
 *  - simple: quick conversion from plain strings to Components.

 * Notes:
 *  - Exposed as an interface with static members/methods (Java pattern for a "final" utils holder).
 *  - MiniMessage is used for styled text; Adventure Components for display.
 */
public interface Components {

    //#573e0dT#21dd11C #b07a1dChallenge >
    /** Global message prefix for the plugin’s chat output, created via MiniMessage. */
    Component PREFIX = Components.mini("BarterChests >> ");

    /** Standard no-permission message, already prefixed+colored. */
    Component NO_PERMISSION = Components.prefixedError(Component.text("You don't have permission for this command!"));


    /** Prefix + gray-colored message body (generic info). */
    static Component prefixedSimpleMessage(Component component) {
        return PREFIX.append(Component.space())
                .append(component.color(NamedTextColor.GRAY));
    }

    /** Prefix + red-colored message body (errors). */
    static Component prefixedError(Component component) {
        return PREFIX.append(Component.space())
                .append(component.color(NamedTextColor.RED));
    }

    /** Prefix + green-colored message body (success). */
    static Component prefixedSuccess(Component component) {
        return PREFIX.append(Component.space())
                .append(component.color(NamedTextColor.GREEN));
    }

    /** Prefix + message without color override (keeps component’s own styling). */
    static Component prefixed(Component component) {
        return PREFIX.append(Component.space())
                .append(component);
    }

    /**
     * Set an item’s display name, forcing non-italic text for readability.
     * (Minecraft defaults many item names to italic; this turns it off.)
     */
    static void name(ItemMeta meta, Component component) {
        meta.displayName(component.decoration(TextDecoration.ITALIC, false));
    }


    /**
     * Set item lore from varargs Components, applying non-italic decoration to each line.
     */
    static void lore(ItemMeta meta, Component... components) {
        List<Component> componentList = new ArrayList<>(components.length);
        for (Component component : components) {
            componentList.add(component.decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(componentList);
    }

    /**
     * Set item lore from a list of Components, applying non-italic decoration to each line.
     */
    static void lore(ItemMeta meta, List<Component> components) {
        List<Component> componentList = new ArrayList<>(components.size());
        for (Component component : components) {
            componentList.add(component.decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(componentList);
    }

    /**
     * Deserialize a multi-line MiniMessage string into a list of Components.
     * - Splits on '\n'
     * - Preserves blank lines as Component.empty()
     * - Applies optional TagResolvers for placeholders/formatting
     */
    static List<Component> miniSplit(String args, TagResolver... resolvers) {
        String[] strings = args.split("\n");
        List<Component> components = new ArrayList<>(strings.length);

        for (String string : strings) {
            if (string.isBlank()) {
                components.add(Component.empty());
                continue;
            }

            components.add(MiniMessage.miniMessage().deserialize(string, resolvers));
        }

        return components;
    }

    /** One-shot MiniMessage -> Component helper with optional resolvers. */
    static Component mini(String string, TagResolver... resolvers) {
        return MiniMessage.miniMessage().deserialize(string, resolvers);
    }

    /** Convert an array of plain strings into an array of plain-text Components. */
    static Component[] simple(String... strings) {
        Component[] components = new Component[strings.length];
        for (int i = 0; i < strings.length; i++) {
            String str = strings[i];
            components[i] = Component.text(str);
        }

        return components;
    }
}
