package com.stifflered.bartercontainer;

import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.stifflered.bartercontainer.util.Messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.*;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Configuration facade for the BarterContainer plugin.

 * Role:
 *  - Loads the plugin config (copying defaults if missing) and exposes typed getters for common sections.
 *  - Converts MiniMessage strings to Adventure Components for titles, buttons, lore, etc.
 *  - Builds configured ItemStacks (icon, name, lore) with optional MiniMessage TagResolvers for placeholders.

 * Notes:
 *  - The constructor triggers save+reload of the config to ensure defaults are present.
 *  - Required sections/keys now fail fast with clear errors (e.g., missing item section or material).
 *  - Optional fields (e.g., item name/lore, tracking messages) are handled gracefully (empty/defaults).
 *  - parse(...) centralizes ItemStack creation (type, name, lore) with validation.
 */
public class BarterContainerConfiguration {

    /** Root configuration handle (backed by plugin's config.yml). */
    private final FileConfiguration section;

    /**
     * Initializes configuration by ensuring defaults are copied, saved, and then reloaded.
     * Sequence ensures that any missing default keys are written to disk before reads occur.
     */
    public BarterContainerConfiguration(BarterContainer plugin) {
        plugin.getConfig().options().copyDefaults(true); // copy defaults from the bundled config.yml into memory
        plugin.saveConfig();                              // persist defaults to disk if not present
        plugin.reloadConfig();                            // reload from disk so 'section' reflects latest file
        this.section = plugin.getConfig();                // cache the FileConfiguration reference for lookups
    }

    /** Returns the configured "shop lister" item (icon/name/lore). */
    public ItemStack getShopListerConfiguration() {
        return parse(section, "shop-lister-item");
    }

    /** Item used to represent a purchasable entry. */
    public ItemStack getBuyItemConfiguration() {
        return parse(section, "buy-item");
    }

    /** Item displayed when a shop entry is out of stock. */
    public ItemStack getOutOfStockItemConfiguration() {
        return parse(section, "out-of-stock-item");
    }

    /** Item shown when the shop container has no free slots. */
    public ItemStack getShopFullItemConfiguration() {
        return parse(section, "shop-full-item");
    }

    /** Item shown when player lacks the price/currency (not enough to purchase). */
    public ItemStack getNotEnoughPriceItemConfiguration() {
        return parse(section, "not-enough-price-item");
    }

    /** MiniMessage string for styled inventory title (raw string, not yet deserialized). */
    public String getStyledBarterTitle() {
        return section.getString("styled-title");
    }

    /** Message template when purchasing from a player (raw string, likely used with MiniMessage elsewhere). */
    public String getPurchaseFromPlayerMessage() {
        return section.getString("purchase-from-player-message");
    }

    /**
     * Structured config for transaction logs: title, hover text, and date pattern (SimpleDateFormat style).
     * These are used where legacy transaction rows (paper/book) are rendered.
     */
    public TransactionLogConfiguration getTransactionLogConfiguration() {
        return new TransactionLogConfiguration(
                section.getString("transactions.title"),
                section.getString("transactions.hover"),
                section.getString("transactions.timeFormat")
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    // NOTE: GUI titles/lore have been migrated to messages.yml and are
    // fetched via Messages.mm / Messages.mmList at call sites. The getters
    // below remain for backward-compat (unused in the new flow).
    // ─────────────────────────────────────────────────────────────────────

    /** Title component for the catalogue GUI (already deserialized via Adventure). */
    public Component getCatalogueTitle() {
        return section.getComponent("catalog.title", MiniMessage.miniMessage());
    }

    /** Lore lines for catalogue items; each string deserialized into a Component. */
    public List<Component> getCatalogueItemLore() {
        return section.getStringList("catalog.item-lore").stream()
                .map(line -> MiniMessage.miniMessage().deserialize(line))
                .toList();
    }

    /** Lore lines for items within the shopping list UI. */
    public List<Component> getShoppingListLore() {
        return section.getStringList("shopping-list.lore").stream()
                .map(line -> MiniMessage.miniMessage().deserialize(line))
                .toList();
    }

    /** Message displayed when an item is added to the shopping list (raw string). */
    public String getShoppingListAddItemMessage() {
        return section.getString("shopping-list.add-item-message");
    }

    /** Message displayed when an item is removed from the shopping list (raw string). */
    public String getShoppingListRemoveItemMessage() {
        return section.getString("shopping-list.remove-item-message");
    }

    /** Component used for the "remove" button in the shopping list UI (typically an X icon/text). */
    public Component getShoppingListX() {
        return section.getComponent("shopping-list.remove-button", MiniMessage.miniMessage());
    }

    /** Component used for the "add" button in the shopping list UI (typically a plus icon/text). */
    public Component getShoppingListPlus() {
        return section.getComponent("shopping-list.add-button", MiniMessage.miniMessage());
    }

    /** Component template for progress display in the shopping list (e.g., 3/10). */
    public Component getShoppingListProgress() {
        return section.getComponent("shopping-list.progress", MiniMessage.miniMessage());
    }

    /** Component used to render a checked-off state for items in the shopping list. */
    public Component getShoppingListCheckedOff() {
        return section.getComponent("shopping-list.checked-off", MiniMessage.miniMessage());
    }

    /** Message shown when a remove action fails (already a Component). */
    public Component getShoppingListNotRemovedItemMessage() {
        return section.getComponent("shopping-list.remove-item-message-fail", MiniMessage.miniMessage());
    }

    /** Display name for the "set price" GUI item. */
    public Component getPriceGuiItemName() {
        return section.getComponent("main.set-price-item.name", MiniMessage.miniMessage());
    }

    /** Lore for the "set price" GUI item (deserialized per line). */
    public List<Component> getPriceGuiItemLore() {
        return section.getStringList("main.set-price-item.lore").stream()
                .map(line -> MiniMessage.miniMessage().deserialize(line))
                .toList();
    }

    /** Item used to open/inspect a specific shop. */
    public ItemStack getViewShopItem() {
        return parse(section, "view-shop-item");
    }

    /**
     * Item stack for "set price", with optional placeholders that are resolved into the MiniMessage content.
     * Callers can pass TagResolvers (e.g., Placeholder.unparsed("price", "5")) to personalize the lore/name.
     */
    public ItemStack getSetPriceItem(TagResolver... placeholders) {
        return parse(section, "set-price-item", placeholders);
    }

    /** Item to open a player's barter bank (currency/storage abstraction). */
    public ItemStack getViewBarterBankItem() {
        return parse(section, "view-barter-bank-item");
    }

    /** Item to open a transaction logs view. */
    public ItemStack getViewLogsItem() {
        return parse(section, "view-logs-item");
    }

    /**
     * Item used for the Catalogue "Search" button shown in the bottom-right of each page.

     * Current YAML (types ONLY; text lives in messages.yml):

     *   catalog-search-button-item:
     *     type: minecraft:compass
     */
    public ItemStack getCatalogueSearchItem() {
        final String path = "catalog-search-button-item";
        final String typePath = path + ".type";

        // Read exactly what the file has (no surprises)
        String raw = section.getString(typePath);

        // Null / missing section? Log and hard-fallback to COMPASS.
        if (raw == null) {
            BarterContainer.INSTANCE.getLogger().warning(Messages.fmt(
                    "config.errors.missing_section",
                    "path", path
            ));
            return new ItemStack(Material.COMPASS, 1);
        }

        Material mat = materialFromString(raw);

        // Invalid / unknown → log and force COMPASS (never Barrier)
        if (mat == null || !mat.isItem()) {
            BarterContainer.INSTANCE.getLogger().warning(Messages.fmt(
                    "config.errors.invalid_material",
                    "path", typePath,
                    "value", raw
            ));
            mat = Material.COMPASS;
        }

        return new ItemStack(mat, 1);
    }

    /** Tuple for transaction log rendering configuration. */
    public record TransactionLogConfiguration(String title, String hover, String timeFormat) {}

    /**
     * Helper to read an item section and construct an ItemStack with name/lore applied.

     * Expected YAML structure (example):
     *   set-price-item:
     *     type: PAPER
     *     name: "<gold>Set Price</gold>"
     *     lore:
     *       - "<gray>Current: <white><price></white></gray>"

     * Behavior:
     *  - Material is looked up via a robust resolver that accepts namespaced keys or plain enum names.
     *  - Name and lore strings are deserialized via MiniMessage (with optional TagResolvers).
     *  - ItemUtil.wrapEdit applies meta changes in a single pass.

     * Pitfalls / validation:
     *  - Missing section path → throws NullPointerException via requireNonNull with a clear message.
     *  - Missing/invalid material → throws IllegalStateException with the offending key/value.
     *  - Name/lore are optional and omitted if absent.
     */
    private static ItemStack parse(ConfigurationSection section, String path, TagResolver... resolvers) {
        // Ensure the item section exists
        ConfigurationSection item = Objects.requireNonNull(
                section.getConfigurationSection(path),
                () -> Messages.fmt("config.errors.missing_section", "path", path)
        );

        // Validate and resolve material (accepts "minecraft:compass", "COMPASS", "compass", etc.)
        String typeName = item.getString("type");
        Material material = materialFromString(typeName);
        if (material == null) {
            throw new IllegalStateException(Messages.fmt(
                    "config.errors.invalid_material",
                    "path", path + ".type",
                    "value", String.valueOf(typeName)
            ));
        }

        ItemStack base = ItemStack.of(material);

        // Optional name/lore (handle gracefully if absent)
        var mm = MiniMessage.miniMessage();
        List<String> loreLines = item.getStringList("lore"); // never null; empty if missing
        Component name = item.getComponent("name", mm);      // may be null

        return ItemUtil.wrapEdit(base, meta -> {
            if (!loreLines.isEmpty()) {
                Components.lore(meta, loreLines.stream()
                        .map(str -> mm.deserialize(str, resolvers))
                        .toList());
            }
            if (name != null) {
                Components.name(meta, name);
            }
        });
    }

    /**
     * Robust Material resolver:
     *  - Accepts namespaced keys (e.g., "minecraft:compass") or plain names ("COMPASS", "compass").
     *  - Case-insensitive.
     *  - Normalizes dashes to underscores.
     */
    private static Material materialFromString(String raw) {
        if (raw == null || raw.isBlank()) return null;

        String s = raw.trim();

        // Strip namespace if present
        int idx = s.indexOf(':');
        if (idx >= 0 && idx < s.length() - 1) {
            s = s.substring(idx + 1);
        }

        // Normalize case / separators for enum lookup
        String enumKey = s
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);

        // Try the strict enum first (fast path)
        try {
            return Material.valueOf(enumKey);
        } catch (IllegalArgumentException ignored) {
            // Fall back to Bukkit's matcher (some implementations support flexible names)
            Material m = Material.matchMaterial(raw);
            if (m != null) return m;

            // As a last resort, try matcher on the cleaned key
            return Material.matchMaterial(enumKey);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 📍 Tracking System — numeric ranges only (messages moved to messages.yml)
    // ─────────────────────────────────────────────────────────────────────
    /**
     * Reads tracking-system settings that drive the navigation guidance:
     *  - track-range: Max distance (blocks) to stop tracking when reached (default 5)
     *  - arrow-range: Length (blocks) of the particle “arrow” (default 3)

     * Source of truth: config.yml → tracking-system
     */
    public TrackingSystemConfiguration getTrackingSystemConfiguration() {
        ConfigurationSection trackingSection = Objects.requireNonNull(
                section.getConfigurationSection("tracking-system"),
                Messages.fmt("config.errors.missing_tracking_section")
        );

        double trackRange = trackingSection.getDouble("track-range", 5);
        double arrowRange = trackingSection.getDouble("arrow-range", 3);

        return new TrackingSystemConfiguration(trackRange, arrowRange);
    }

    /** Container for tracking numeric ranges. */
    public record TrackingSystemConfiguration(double trackRange, double arrowRange) {}

    // ─────────────────────────────────────────────────────────────────────
    // 🕓 Shop Stats — Absolute Timestamp Formatter (UTC)
    // ─────────────────────────────────────────────────────────────────────
    /**
     * Returns the DateTimeFormatter used for absolute timestamps in Shop Stats tiles/tooltips.

     * Source of truth: config.yml → transactions.time_pattern
     *   - Example default: "yyyy-MM-dd HH:mm 'UTC'"
     *   - Always forced to UTC, regardless of server TZ.

     * If the pattern is invalid, we fall back to the default above.
     */
    public DateTimeFormatter getShopStatsAbsoluteFormatter() {
        final String pattern = section.getString("transactions.time_pattern", "yyyy-MM-dd HH:mm 'UTC'");
        try {
            return DateTimeFormatter.ofPattern(pattern).withZone(ZoneOffset.UTC);
        } catch (IllegalArgumentException ex) {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);
        }
    }
}
