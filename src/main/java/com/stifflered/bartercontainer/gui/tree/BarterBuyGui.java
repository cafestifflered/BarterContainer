package com.stifflered.bartercontainer.gui.tree;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.gui.tree.buttons.SetPriceGuiItem;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.stifflered.bartercontainer.util.Sounds;
import com.stifflered.bartercontainer.util.BarterShopOwnerLogManager;
import com.stifflered.bartercontainer.util.Messages;

import me.sashak.inventoryutil.ItemRemover;
import me.sashak.inventoryutil.slotgroup.SlotGroups;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Buyer-facing GUI for a single shop. Presents:
 *  - Price item display (top center)
 *  - A grid of purchasable items (bottom 3 rows)
 *  - A primary action slot (center) that changes between:
 *      • "Open Shop" (buy)
 *      • "Out of Stock"
 *      • "Full Shop" (currency bank full)
 *      • "Can't afford!" (player lacks price items)

 * Interaction flow:
 *  1) On first click of a specific item, show a green "☑ Confirm Purchase" in the center.
 *  2) On clicking that confirm item, run {@link #buy(Player, ItemStack, BuySlot, BarterStore)}.

 * Safety checks before enabling purchase:
 *  - Player must have the required price item(s).
 *  - Store's currency inventory must have room to accept payment.
 *  - Store must have at least one sale item.
 */
public class BarterBuyGui extends ChestGui {

    // UI components sourced from config.yml for consistent styling/text
    private static final ItemStack BUY_ITEM = BarterContainer.INSTANCE
            .getConfiguration()
            .getBuyItemConfiguration();

    private static final ItemStack OUT_OF_STOCK = BarterContainer.INSTANCE
            .getConfiguration()
            .getOutOfStockItemConfiguration();

    private static final ItemStack SHOP_FULL = BarterContainer.INSTANCE
            .getConfiguration()
            .getShopFullItemConfiguration();

    private static final ItemStack NOT_ENOUGH_PRICE = BarterContainer.INSTANCE
            .getConfiguration()
            .getNotEnoughPriceItemConfiguration();

    /** Gate to prevent confirming when pre-checks fail. */
    private boolean canBuy = false;

    /**
     * Build a 5-row purchase UI for this store, titled with the shop's styled name.

     * Layout:
     *  - Row 0..1: Grey glass framing + price item at (4,0)
     *  - Row 2..4: Clickable grid of items for sale
     *  - Center slot (4,1): Primary action / status (depends on checks)
     */
    public BarterBuyGui(Player player, BarterStore store) {
        super(5, ComponentHolder.of(store.getNameStyled()));
        // Make inventory read-only except for our handlers
        this.setOnGlobalClick((event) -> event.setCancelled(true));

        // Top framing rows
        StaticPane pane = ItemUtil.wrapGui(this.getInventoryComponent(), 9, 5);
        for (int i = 0; i < 9; i++) {
            GuiItem item = new GuiItem(new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
            pane.addItem(item, i, 0);
            pane.addItem(item, i, 1);
        }

        // Show the current price item (read-only) in top-center
        pane.addItem(new GuiItem(SetPriceGuiItem.getPriceItem(store)),4, 0);
        // pane.addItem(new GuiItem(BUY_ITEM_ARROW),0, 1);

        // Item display grid (3 rows starting at y=2). Each item sets up a confirm button in (4,1).
        StaticPane itemDisplay = ItemUtil.wrapGui(this.getInventoryComponent(), 0, 2, 9, 3);
        {
            ItemUtil.listItems(itemDisplay, getBuyItems(store).stream().filter(Objects::nonNull).map(buySlot -> new GuiItem(buySlot.item(), mainBuyClick -> {

                if (!this.canBuy) {
                    // Guard: do nothing if purchase is disabled by pre-checks
                    return;
                }
                Sounds.choose(player);

                // Snapshot the item currently in that slot to avoid race conditions
                ItemStack previewItem = Objects.requireNonNullElse(
                        store.getSaleStorage().getItem(buySlot.slot()),
                        new ItemStack(Material.AIR)
                ).clone();

                // Place a "Confirm Purchase" button in center (4,1)
                // NOTE: Do NOT reuse the preview item's meta — build a clean icon so name/lore stick (fixes candle cases).
                ItemStack confirmIcon = new ItemStack(previewItem.getType(), Math.max(1, previewItem.getAmount()));
                ItemUtil.wrapEdit(confirmIcon, (meta) -> {
                    // NAME via messages.yml (fallbacks to path string if key missing)
                    Components.name(meta, Messages.mm("buy.confirm_name"));
                    // LORE via messages.yml list (never null)
                    java.util.List<Component> loreLines = Messages.mmList("buy.confirm_lore");
                    if (loreLines.isEmpty()) {
                        // Safe fallback (kept minimal)
                        Components.lore(meta, Components.miniSplit("""
                                <gray>Click to <green>confirm</green>
                                <gray>your purchase."""));
                    } else {
                        meta.lore(loreLines);
                    }
                    ItemUtil.glow(meta); // Visual emphasis
                });

                pane.addItem(new GuiItem(
                        confirmIcon,
                        (event) -> this.buy((Player) event.getWhoClicked(), previewItem, buySlot, store)
                ), 4, 1);

                BarterBuyGui.this.update();
            })).toList());
        }

        // Pre-purchase checks determine the center action/status item
        boolean hasSlots = me.sashak.inventoryutil.ItemUtil.hasAllItems(player, SlotGroups.PLAYER_ENTIRE_INV, store.getCurrentItemPrice());
        if (!hasSlots) {
            pane.addItem(notEnoughPriceItem(), 4, 1);
            return;
        }
        boolean hasRoom = me.sashak.inventoryutil.ItemUtil.hasRoomForItems(store.getCurrencyStorage(), SlotGroups.ENTIRE_INV, store.getCurrentItemPrice());
        if (!hasRoom) {
            pane.addItem(shopFullItem(), 4, 1);
            return;
        }

        boolean isEmpty = store.getSaleStorage().isEmpty();
        if (isEmpty) {
            pane.addItem(outOfStockItem(), 4, 1);
            return;
        }

        // All good → show the green "Open Shop" (confirm-able) item and allow buy flow
        pane.addItem(openShopItem(), 4, 1);
        this.canBuy = true;
    }


    /**
     * Finalize a purchase after the player has clicked "Confirm Purchase".

     * Safeguards:
     *  - Re-verify that the previewed item still matches the slot's current item (no race).
     *  - Ensure the player's payment can be deposited; otherwise stop.
     *  - Remove payment from player, remove item from store, deliver item to player.
     *  - Log transaction + notify store owner if online.
     *  - Attempt to progress the player's shopping list based on item received.
     *  - Save store state and refresh the GUI.
     */
    private void buy(Player player, ItemStack previewItem, BuySlot buySlot, BarterStore store) {
        // Re-check: the item hasn't changed since preview (another buyer might have grabbed it)
        ItemStack itemStack = Objects.requireNonNullElse(
                store.getSaleStorage().getItem(buySlot.slot()),
                new ItemStack(Material.AIR)
        ).clone();

        if (!previewItem.equals(itemStack)) {
            // messages.yml-driven error with sound
            Messages.error(player, "buy.already_taken");
            player.closeInventory();
            return;
        }

        Sounds.purchase(player);

        // Ensure player still has the required price items
        if (me.sashak.inventoryutil.ItemUtil.hasAllItems(player, SlotGroups.PLAYER_ENTIRE_INV, store.getCurrentItemPrice())) {
            // Try to deposit payment into the shop's currency inventory
            HashMap<Integer, ItemStack> leftOver = store.getCurrencyStorage().addItem(store.getCurrentItemPrice());
            if (leftOver.isEmpty()) {
                // Remove payment from player
                ItemRemover.removeItems(player, SlotGroups.PLAYER_ENTIRE_INV, store.getCurrentItemPrice());
                // Remove the purchased item from the store
                store.getSaleStorage().setItem(buySlot.slot(), null);

                // Give the item to the buyer (drop if inventory full)
                ItemUtil.giveItemOrThrow(player, itemStack);

                // ---------------------------------------------------------------------------------
                // Append a single v3 transaction line with the full purchased ItemStack (meta/NBT)
                // and a representative "price display" snapshot. This is the canonical log.
                // ---------------------------------------------------------------------------------
                ItemStack priceDisplay = SetPriceGuiItem.getPriceItem(store); // UI-facing price snapshot
                try {
                    BarterShopOwnerLogManager.addLog(
                            store.getKey(),
                            System.currentTimeMillis(),
                            player.getUniqueId(),
                            player.getName(),
                            itemStack,     // exact delivered item (with meta)
                            priceDisplay   // decorative/representative price item
                    );
                } catch (java.io.IOException ioe) {
                    // Non-fatal: purchase succeeded; just log the failure to write the log line
                    BarterContainer.INSTANCE.getLogger().warning("Failed to append purchase log: " + ioe.getMessage());
                }

                // Notify owner if they are online
                UUID ownerId = null;
                try {
                    if (store.getPlayerProfile() != null) {
                        ownerId = store.getPlayerProfile().getId();
                    }
                } catch (Throwable ignored) {
                    // be defensive against API differences
                }
                if (ownerId != null) {
                    Player owner = Bukkit.getPlayer(ownerId);
                    if (owner != null) {
                        // The visible label for <type> — prefer custom display name if present
                        String typeLabel = Optional.ofNullable(itemStack.getItemMeta())
                                .map(ItemMeta::displayName)
                                .map(PlainTextComponentSerializer.plainText()::serialize)
                                .map(String::trim)
                                .filter(s -> !s.isBlank())
                                .orElse(pretty(itemStack.getType()));

                        // Build <type> component with conditional hover (shows only if there are details)
                        Component typeBase = Component.text(typeLabel, net.kyori.adventure.text.format.NamedTextColor.WHITE);
                        Component hover = buildItemHover(itemStack); // null when nothing to show
                        Component typeWithHover = (hover != null)
                                ? typeBase.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hover))
                                : typeBase;

                        // message via messages.yml path "buy.owner_notify"
                        owner.sendMessage(Messages.mm(
                                "buy.owner_notify",
                                "type", typeWithHover,
                                "count", String.valueOf(itemStack.getAmount()),
                                "purchaser", player.getName()
                        ));
                    }
                }

                // Try to update the buyer's shopping list based on the received item
                Component message = switch (BarterContainer.INSTANCE.getShoppingListManager().receive(player, itemStack)) {
                    case MODIFIED -> BarterContainer.INSTANCE.getConfiguration().getShoppingListProgress();
                    case REMOVED -> BarterContainer.INSTANCE.getConfiguration().getShoppingListCheckedOff();
                    case NOTHING -> null;
                };
                if (message != null) {
                    player.sendMessage(message);
                }

                // Persist store changes
                BarterManager.INSTANCE.save(store);
            }
            // Refresh the GUI for the buyer (reflects stock/payment changes)
            new BarterBuyGui(player, store).show(player);
        }
    }

    /**
     * Collects all non-null items in the sale storage along with their slot indices.
     * Used to render the clickable buying grid.
     */
    public static List<BuySlot> getBuyItems(BarterStore store) {
        List<BuySlot> itemStacks = new ArrayList<>();

        ListIterator<ItemStack> iterator = store.getSaleStorage().iterator();
        while (iterator.hasNext()) {
            int slot = iterator.nextIndex();
            ItemStack itemStack = iterator.next();
            if (itemStack != null) {
                itemStacks.add(new BuySlot(slot, itemStack.clone()));
            }
        }

        return itemStacks;
    }

    /** Tuple for "slot index + item copy" for purchase previews. */
    public record BuySlot(int slot, ItemStack item) { }

    /* ------------------------------- Pretty Helpers ------------------------------- */

    /** Material -> "Nice Name" (e.g., DIAMOND_SWORD -> "Diamond Sword"). */
    private static String pretty(Material m) {
        String s = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return capWords(s);
    }

    private static String capWords(String in) {
        StringBuilder out = new StringBuilder(in.length());
        boolean cap = true;
        for (char c : in.toCharArray()) {
            if (cap && Character.isLetter(c)) {
                out.append(Character.toUpperCase(c));
                cap = false;
            } else {
                out.append(c);
            }
            if (c == ' ') cap = true;
        }
        return out.toString();
    }

    // --- inline helpers for <type> hover ---

    private static Component buildItemHover(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return null;

        java.util.List<Component> lines = new java.util.ArrayList<>(8);

        // Custom Name (only if different from pretty material)
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        String custom = customName(meta);
        String basePretty = pretty(stack.getType());
        if (custom != null && !custom.equalsIgnoreCase(basePretty)) {
            lines.add(Messages.mm("buy.hover.name")
                    .append(Component.text(custom, net.kyori.adventure.text.format.NamedTextColor.WHITE)));
        }

        // Enchantments
        java.util.Map<org.bukkit.enchantments.Enchantment, Integer> ench = (meta != null ? meta.getEnchants() : java.util.Map.of());
        if (!ench.isEmpty()) {
            java.util.List<String> parts = new java.util.ArrayList<>(ench.size());
            for (java.util.Map.Entry<org.bukkit.enchantments.Enchantment, Integer> e : ench.entrySet()) {
                parts.add(prettyEnchant(e.getKey()) + " " + roman(e.getValue()));
            }
            lines.add(Messages.mm("buy.hover.enchants")
                    .append(Component.text(String.join(", ", parts), net.kyori.adventure.text.format.NamedTextColor.WHITE)));
        }

        // Enchanted Book stored enchants
        if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta esm && !esm.getStoredEnchants().isEmpty()) {
            java.util.List<String> parts = new java.util.ArrayList<>(esm.getStoredEnchants().size());
            for (java.util.Map.Entry<org.bukkit.enchantments.Enchantment, Integer> e : esm.getStoredEnchants().entrySet()) {
                parts.add(prettyEnchant(e.getKey()) + " " + roman(e.getValue()));
            }
            lines.add(Messages.mm("buy.hover.book_enchants")
                    .append(Component.text(String.join(", ", parts), net.kyori.adventure.text.format.NamedTextColor.WHITE)));
        }

        // Potion details (base potion + custom effects) for Potion/Splash/Lingering/Tipped Arrow
        if (meta instanceof org.bukkit.inventory.meta.PotionMeta pm) {
            // Base potion (guarded to support older APIs)
            try {
                org.bukkit.potion.PotionType base = pm.getBasePotionType();
                if (base != null) {
                    String kindKey = switch (stack.getType()) {
                        case SPLASH_POTION -> "buy.hover.splash";
                        case LINGERING_POTION -> "buy.hover.lingering";
                        case TIPPED_ARROW -> "buy.hover.arrow";
                        default -> "buy.hover.potion";
                    };
                    lines.add(Messages.mm(kindKey).append(
                            net.kyori.adventure.text.Component.text(
                                    capWords(base.name().toLowerCase(java.util.Locale.ROOT).replace('_',' ')),
                                    net.kyori.adventure.text.format.NamedTextColor.WHITE
                            )
                    ));
                }
            } catch (Throwable ignored) {}

            // Custom effects (if any)
            var effects = pm.getCustomEffects(); // never null
            if (!effects.isEmpty()) {
                lines.add(Messages.mm("buy.hover.effects_header"));
                for (var fx : effects) {
                    // Use key (non-deprecated) instead of getName()
                    String name = capWords(fx.getType().getKey().getKey().toLowerCase(java.util.Locale.ROOT).replace('_',' '));
                    int level = Math.max(1, fx.getAmplifier() + 1);
                    int secs = Math.max(0, fx.getDuration() / 20);
                    lines.add(Messages.mm("buy.hover.effect_line",
                            "name", name,
                            "level", roman(level),
                            "seconds", secs));
                }
            }
        }

        // Shulker contents (and Bundles via reflection if you use them)
        java.util.List<Component> contents = summarizeContainerContents(stack);
        if (!contents.isEmpty()) {
            lines.add(Messages.mm("buy.hover.contents_header"));
            lines.addAll(contents);
        }

        if (lines.isEmpty()) return null;
        return Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines);
    }

    private static String customName(org.bukkit.inventory.meta.ItemMeta meta) {
        if (meta == null) return null;
        try {
            Component c = meta.displayName();
            if (c == null) return null;
            String plain = PlainTextComponentSerializer.plainText().serialize(c).trim();
            return plain.isEmpty() ? null : plain;
        } catch (Throwable t) {
            try {
                @SuppressWarnings("deprecation")
                String legacy = meta.hasDisplayName() ? meta.getDisplayName() : "";
                return legacy.isBlank() ? null : legacy;
            } catch (Throwable ignored) { return null; }
        }
    }

    private static String prettyEnchant(org.bukkit.enchantments.Enchantment e) {
        try {
            org.bukkit.NamespacedKey k = e.getKey();
            return capWords(k.getKey().replace('_', ' '));
        } catch (Throwable ignored) {
            return "Enchantment";
        }
    }

    private static String roman(int n) {
        String[] M = {"", "M", "MM", "MMM"};
        String[] C = {"", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"};
        String[] X = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
        String[] I = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        return M[(n/1000)%10] + C[(n/100)%10] + X[(n/10)%10] + I[n%10];
    }

    private static java.util.List<Component> summarizeContainerContents(ItemStack s) {
        java.util.List<Component> out = new java.util.ArrayList<>();
        org.bukkit.inventory.meta.ItemMeta meta = s.getItemMeta();

        // Shulker boxes
        if (meta instanceof org.bukkit.inventory.meta.BlockStateMeta bsm
                && bsm.getBlockState() instanceof org.bukkit.block.ShulkerBox box) {

            ItemStack[] contents = box.getInventory().getContents();
            java.util.List<Component> lines = describeContents(java.util.Arrays.asList(contents));
            out.addAll(lines);
        }
        // Bundles (reflective, safe if not present)
        else if (isBundleMeta(meta)) {
            java.util.List<ItemStack> items = bundleItems(meta);
            java.util.List<Component> lines = describeContents(items);
            out.addAll(lines);
        }
        return out;
    }

    private static final int MAX_CONTENT_LINES = 6;

    private static java.util.List<Component> describeContents(java.util.Collection<ItemStack> items) {
        java.util.List<Component> lines = new java.util.ArrayList<>();
        int shown = 0, skipped = 0;
        for (ItemStack it : items) {
            if (it == null || it.getType() == Material.AIR) continue;
            String name = (it.hasItemMeta() ? customName(it.getItemMeta()) : null);
            if (name == null || name.isBlank()) name = pretty(it.getType());
            String ench = "";
            if (it.hasItemMeta() && !it.getItemMeta().getEnchants().isEmpty()) {
                java.util.List<String> parts = new java.util.ArrayList<>();
                for (java.util.Map.Entry<org.bukkit.enchantments.Enchantment, Integer> e : it.getItemMeta().getEnchants().entrySet()) {
                    parts.add(prettyEnchant(e.getKey()) + " " + roman(e.getValue()));
                }
                ench = " (" + String.join(", ", parts) + ")";
            }
            String text = Math.max(1, it.getAmount()) + "× " + name + ench;
            if (shown < MAX_CONTENT_LINES) {
                lines.add(Messages.mm("buy.hover.contents_line", "item", text));
                shown++;
            } else {
                skipped++;
            }
        }
        if (skipped > 0) {
            lines.add(Messages.mm("buy.hover.contents_more", "count", skipped));
        }
        return lines;
    }

    private static boolean isBundleMeta(org.bukkit.inventory.meta.ItemMeta meta) {
        if (meta == null) return false;
        try {
            meta.getClass().getMethod("getItems"); // presence check only
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<ItemStack> bundleItems(org.bukkit.inventory.meta.ItemMeta meta) {
        try {
            var m = meta.getClass().getMethod("getItems");
            Object o = m.invoke(meta);
            if (o instanceof java.util.List) return (java.util.List<ItemStack>) o;
        } catch (Throwable ignored) {}
        return java.util.Collections.emptyList();
    }

    /* ------------------------------- UI Item Builders ------------------------------- */

    /** Wraps the configured "Open Shop" icon with name/lore from messages.yml. */
    private static GuiItem openShopItem() {
        ItemStack base = BUY_ITEM.clone();
        ItemUtil.wrapEdit(base, meta -> {
            Components.name(meta, Messages.mm("gui.main.open_shop_name"));
            java.util.List<Component> lore = Messages.mmList("gui.main.open_shop_lore");
            if (!lore.isEmpty()) meta.lore(lore);
        });
        return new GuiItem(base);
    }

    /** Wraps the configured "Out of Stock" icon with name/lore from messages.yml. */
    private static GuiItem outOfStockItem() {
        ItemStack base = OUT_OF_STOCK.clone();
        ItemUtil.wrapEdit(base, meta -> {
            Components.name(meta, Messages.mm("gui.main.out_of_stock_name"));
            java.util.List<Component> lore = Messages.mmList("gui.main.out_of_stock_lore");
            if (!lore.isEmpty()) meta.lore(lore);
        });
        return new GuiItem(base);
    }

    /** Wraps the configured "Shop Full" icon with name/lore from messages.yml. */
    private static GuiItem shopFullItem() {
        ItemStack base = SHOP_FULL.clone();
        ItemUtil.wrapEdit(base, meta -> {
            Components.name(meta, Messages.mm("gui.main.shop_full_name"));
            java.util.List<Component> lore = Messages.mmList("gui.main.shop_full_lore");
            if (!lore.isEmpty()) meta.lore(lore);
        });
        return new GuiItem(base);
    }

    /** Wraps the configured "Not Enough Price" icon with name/lore from messages.yml. */
    private static GuiItem notEnoughPriceItem() {
        ItemStack base = NOT_ENOUGH_PRICE.clone();
        ItemUtil.wrapEdit(base, meta -> {
            Components.name(meta, Messages.mm("gui.main.not_enough_price_name"));
            java.util.List<Component> lore = Messages.mmList("gui.main.not_enough_price_lore");
            if (!lore.isEmpty()) meta.lore(lore);
        });
        return new GuiItem(base);
    }
}
