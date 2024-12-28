package com.stifflered.bartercontainer;

import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.sun.source.util.Plugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class BarterContainerConfiguration {

    private final FileConfiguration section;

    public BarterContainerConfiguration(BarterContainer plugin) {
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        plugin.reloadConfig();
        this.section = plugin.getConfig();
    }

    public ItemStack getShopListerConfiguration() {
        return parse(section, "shop-lister-item");
    }

    public ItemStack getBuyItemConfiguration() {
        return parse(section, "buy-item");
    }

    public ItemStack getOutOfStockItemConfiguration() {
        return parse(section, "out-of-stock-item");
    }

    public ItemStack getShopFullItemConfiguration() {
        return parse(section, "shop-full-item");
    }

    public ItemStack getNotEnoughPriceItemConfiguration() {
        return parse(section, "not-enough-price-item");
    }

    public String getStyledBarterTitle() {
        return section.getString("styled-title");
    }

    public String getPurchaseFromPlayerMessage() {
        return section.getString("purchase-from-player-message");
    }

    private static ItemStack parse(ConfigurationSection section, String path) {
        ConfigurationSection item = section.getConfigurationSection(path);

        return ItemUtil.wrapEdit(ItemStack.of(Material.matchMaterial(item.getString("type"))), (meta) -> {
            Components.lore(meta, item.getStringList("lore").stream().map(MiniMessage.miniMessage()::deserialize).toList());
            Components.name(meta, item.getComponent("name", MiniMessage.miniMessage()));
        });
    }

}
