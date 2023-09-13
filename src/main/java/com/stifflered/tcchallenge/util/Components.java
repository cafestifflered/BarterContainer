package com.stifflered.tcchallenge.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public interface Components {

    //#573e0dT#21dd11C #b07a1dChallenge >
    Component PREFIX = Components.mini("<#573e0d>T<#21dd11>C <#b07a1d>Challenge >");

    Component NO_PERMISSION = Components.prefixedError(Component.text("You don't have permission for this command!"));


    static Component prefixedSimpleMessage(Component component) {
        return PREFIX.append(Component.space())
                .append(component.color(NamedTextColor.GRAY));
    }

    static Component prefixedError(Component component) {
        return PREFIX.append(Component.space())
                .append(component.color(NamedTextColor.RED));
    }

    static Component prefixedSuccess(Component component) {
        return PREFIX.append(Component.space())
                .append(component.color(NamedTextColor.GREEN));
    }

    static Component prefixed(Component component) {
        return PREFIX.append(Component.space())
                .append(component);
    }

    static void name(ItemMeta meta, Component component) {
        meta.displayName(component);
    }


    static void lore(ItemMeta meta, Component... components) {
        List<Component> componentList = new ArrayList<>(components.length);
        for (Component component : components) {
            componentList.add(component.decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(componentList);
    }

    static void lore(ItemMeta meta, List<Component> components) {
        List<Component> componentList = new ArrayList<>(components.size());
        for (Component component : components) {
            componentList.add(component.decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(componentList);
    }

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

    static Component mini(String string, TagResolver... resolvers) {
        return MiniMessage.miniMessage().deserialize(string, resolvers);
    }

    static Component[] simple(String... strings) {
        Component[] components = new Component[strings.length];
        for (int i = 0; i < strings.length; i++) {
            String str = strings[i];
            components[i] = Component.text(str);
        }

        return components;
    }
}
