package com.stifflered.bartercontainer;

import com.stifflered.bartercontainer.util.Components;
import com.sun.source.util.Plugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class BarterContainerConfiguration {

    private final FileConfiguration section;

    public BarterContainerConfiguration(BarterContainer plugin) {
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        plugin.reloadConfig();
        this.section = plugin.getConfig();
    }

    public ShopListerConfig getShopListerConfiguration() {
        return new ShopListerConfig(
                Material.matchMaterial(section.getString("shop-lister-item.type")),
                section.getComponent("shop-lister-item.name", MiniMessage.miniMessage()),
                section.getStringList("shop-lister-item.lore").stream().map(MiniMessage.miniMessage()::deserialize).toList()
        );
    }

    public String getStyledBarterTitle() {
        return section.getString("styled-title");
    }

    public record ShopListerConfig(Material material, Component name, List<Component> lore) {

    }

}
