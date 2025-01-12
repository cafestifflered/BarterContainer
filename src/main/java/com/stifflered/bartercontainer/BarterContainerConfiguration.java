package com.stifflered.bartercontainer;

import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.sun.source.util.Plugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.*;
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

    public TransactionLogConfiguration getTransactionLogConfiguration() {
        return new TransactionLogConfiguration(
                section.getString("transactions.title"),
                section.getString("transactions.hover"),
                section.getString("transactions.timeFormat")
        );
    }

    public Component getCatalogueTitle() {
        return section.getComponent("catalog.title", MiniMessage.miniMessage());
    }

    public List<Component> getCatalogueItemLore() {
        return section.getStringList("catalog.item-lore").stream()
                .map(line -> MiniMessage.miniMessage().deserialize(line))
                .toList();
    }

    public List<Component> getShoppingListLore() {
        return section.getStringList("shopping-list.lore").stream()
                .map(line -> MiniMessage.miniMessage().deserialize(line))
                .toList();
    }

    public String getShoppingListAddItemMessage() {
        return section.getString("shopping-list.add-item-message");
    }

    public String getShoppingListRemoveItemMessage() {
        return section.getString("shopping-list.remove-item-message");
    }

    public Component getShoppingListNotRemovedItemMessage() {
        return section.getComponent("shopping-list.remove-item-message-fail", MiniMessage.miniMessage());
    }

    public Component getPriceGuiItemName() {
        return section.getComponent("main.set-price-item.name", MiniMessage.miniMessage());
    }

    public List<Component> getPriceGuiItemLore() {
        return section.getStringList("main.set-price-item.lore").stream()
                .map(line -> MiniMessage.miniMessage().deserialize(line))
                .toList();
    }


    public ItemStack getViewShopItem() {
        return parse(section, "view-shop-item");
    }

    public ItemStack getSetPriceItem(TagResolver... placeholders) {
        return parse(section, "set-price-item", placeholders);
    }

    public ItemStack getViewBarterBankItem() {
        return parse(section, "view-barter-bank-item");
    }

    public ItemStack getViewLogsItem() {
        return parse(section, "view-logs-item");
    }



    public record TransactionLogConfiguration(String title, String hover, String timeFormat) {

    }

    private static ItemStack parse(ConfigurationSection section, String path, TagResolver... resolvers) {
        ConfigurationSection item = section.getConfigurationSection(path);

        return ItemUtil.wrapEdit(ItemStack.of(Material.matchMaterial(item.getString("type"))), (meta) -> {
            Components.lore(meta, item.getStringList("lore").stream().map((str) -> MiniMessage.miniMessage().deserialize(str, resolvers)).toList());
            Components.name(meta, item.getComponent("name", MiniMessage.miniMessage()));
        });
    }

}
