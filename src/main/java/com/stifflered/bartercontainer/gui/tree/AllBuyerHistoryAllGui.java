package com.stifflered.bartercontainer.gui.tree;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.store.BarterStoreKey;
import com.stifflered.bartercontainer.util.BarterShopOwnerLogManager;
import com.stifflered.bartercontainer.util.BarterShopOwnerLogManager.TransactionRecord;
import com.stifflered.bartercontainer.util.Messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta; // already present
import org.bukkit.potion.PotionType;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

import java.time.Instant;

import java.util.*;

import static net.kyori.adventure.text.Component.text;

/**
 * Unfiltered "All Purchases (All Barrels)" (Large Chest, 54).
 * Newest → oldest across all provided keys. Bottom: Prev(45), Back(49), Next(53).
 */
public final class AllBuyerHistoryAllGui {

    /* ------------------------------------------------------------------------
     * Title now comes from messages.yml (all_buyer_history_all.title_*).
     * --------------------------------------------------------------------- */
    private static Component title(String ownerLabel) {
        return (ownerLabel == null || ownerLabel.isBlank())
                ? Messages.mm("gui.tree.all_buyer_history_all.title_ownerless")
                : Messages.mm("gui.tree.all_buyer_history_all.title_with_owner", "owner", ownerLabel);
    }

    public static final int SIZE = 54;
    private static final int CONTENT = 45;
    private static final int SLOT_PREV = 45, SLOT_BACK = 49, SLOT_NEXT = 53;

    private static java.time.format.DateTimeFormatter DATE() {
        return com.stifflered.bartercontainer.util.TimeUtil.absoluteFormatter();
    }

    public static final class Holder implements InventoryHolder {
        private final Set<BarterStoreKey> keys;
        private final String ownerLabel;
        private final BarterStoreKey originKey;
        private final int page;
        private volatile int pages = 1;
        private Inventory inventory;
        public Holder(Collection<BarterStoreKey> keys, String ownerLabel, BarterStoreKey originKey, int page) {
            this.keys = Set.copyOf(keys);
            this.ownerLabel = ownerLabel;
            this.originKey = originKey;
            this.page = Math.max(0, page);
        }
        public Set<BarterStoreKey> getKeys() { return keys; }
        public String getOwnerLabel() { return ownerLabel; }
        public BarterStoreKey getOriginKey() { return originKey; }
        public int getPage() { return page; }
        public int getPages() { return pages; }
        void setPages(int pages) { this.pages = Math.max(1, pages); }
        void attach(Inventory inv) { this.inventory = inv; }
        @Override public @NotNull Inventory getInventory() {
            if (inventory == null) inventory = Bukkit.createInventory(this, SIZE, title(ownerLabel));
            return inventory;
        }
    }

    public static void open(Player player, Collection<BarterStoreKey> keys, String ownerLabel, BarterStoreKey originKey, int page) {
        Holder holder = new Holder(keys, ownerLabel, originKey, page);
        Inventory inv = Bukkit.createInventory(holder, SIZE, title(ownerLabel));
        holder.attach(inv);

        drawChrome(inv, page, 1);
        player.openInventory(inv);

        Bukkit.getScheduler().runTaskAsynchronously(BarterContainer.INSTANCE, () -> {
            List<TransactionRecord> all = loadAll(keys);
            all.sort(Comparator.comparingLong(TransactionRecord::timestamp).reversed());
            int pages = Math.max(1, (int) Math.ceil(all.size() / (double) CONTENT));

            Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> {
                if (!player.isOnline()) return;
                Inventory top = player.getOpenInventory().getTopInventory();
                if (!(top.getHolder() instanceof Holder h)) return;
                if (!h.getKeys().equals(Set.copyOf(keys))) return;
                if (!Objects.equals(h.getOriginKey(), originKey)) return; // NEW
                if (h.getPage() != page) return;

                h.setPages(pages);
                fillPapers(top, all, page);
                drawChrome(top, page, pages);
            });
        });
    }

    private static List<TransactionRecord> loadAll(Collection<BarterStoreKey> keys) {
        List<TransactionRecord> out = new ArrayList<>(256);
        try {
            for (BarterStoreKey k : keys) out.addAll(BarterShopOwnerLogManager.listAllEntries(k));
        } catch (Exception ignored) {}
        return out;
    }

    /* ------------------------------------------------------------------------
     * Chrome (nav row) now uses messages.yml:
     *   all_buyer_history_all.nav_prev_name, nav_next_name, nav_page_label
     *   all_buyer_history_all.back_name,  back_lore
     * --------------------------------------------------------------------- */
    private static void drawChrome(Inventory inv, int page, int totalPages) {
        ItemStack pane = pane();
        for (int i = 45; i < 54; i++) inv.setItem(i, pane);

        ItemStack prev = arrow(PotionType.HEALING);
        Component prevName = Messages.mm("gui.tree.all_buyer_history_all.nav_prev_name");
        List<Component> prevLore = List.of(
                Messages.mm("gui.tree.all_buyer_history_all.nav_page_label", "page", page + 1, "pages", totalPages)
        );
        named(prev, prevName, prevLore);
        inv.setItem(SLOT_PREV, prev);

        inv.setItem(SLOT_BACK, backToBuyerListButton());

        ItemStack next = arrow(PotionType.LUCK);
        Component nextName = Messages.mm("gui.tree.all_buyer_history_all.nav_next_name");
        List<Component> nextLore = List.of(
                Messages.mm("gui.tree.all_buyer_history_all.nav_page_label", "page", page + 1, "pages", totalPages)
        );
        named(next, nextName, nextLore);
        inv.setItem(SLOT_NEXT, next);
    }

    private static void fillPapers(Inventory inv, List<TransactionRecord> all, int page) {
        for (int i = 0; i < CONTENT; i++) inv.setItem(i, null);
        if (all == null || all.isEmpty()) return;

        int size = all.size();
        int pages = Math.max(1, (int) Math.ceil(size / (double) CONTENT));
        int safePage = Math.max(0, Math.min(page, pages - 1));

        int from = safePage * CONTENT;
        if (from >= size) return;
        int to = Math.min(from + CONTENT, size);
        List<TransactionRecord> slice = all.subList(from, to);

        for (int idx = 0; idx < slice.size(); idx++) {
            TransactionRecord r = slice.get(idx);

            ItemStack purchased = resolvePurchasedStack(r).orElse(null);

            String when = DATE().format(Instant.ofEpochMilli(r.timestamp()));
            String buyer = (r.purchaserName() == null || r.purchaserName().isBlank())
                    ? r.purchaserUuid().toString() : r.purchaserName();

            // Title comes from messages.yml:
            //  - paper_title_from_stack: "<name> × <amount>"
            //  - paper_title_fallback:   "<item> × <amount>"
            Component titleComp;
            if (purchased != null) {
                String baseName = stackName(purchased);
                titleComp = Messages.mm("gui.tree.all_buyer_history_all.paper_title_from_stack",
                        "name", baseName,
                        "amount", Math.max(0, r.amount()));
            } else {
                titleComp = Messages.mm("gui.tree.all_buyer_history_all.paper_title_fallback",
                        "item", pretty(r.itemType()),
                        "amount", Math.max(0, r.amount()));
            }

            // Lore block from messages.yml (only add details section if needed)
            List<Component> lore = new ArrayList<>(6);
            lore.add(Messages.mm("gui.tree.all_buyer_history_all.lore_buyer", "buyer", buyer));
            lore.add(Messages.mm("gui.tree.all_buyer_history_all.lore_when",  "when",  when));
            lore.add(Messages.mm("gui.tree.all_buyer_history_all.lore_price", "price", priceLabel(r.price())));

            if (purchased != null) {
                List<Component> details = extraLoreFor(purchased);
                if (!details.isEmpty()) {
                    lore.add(Messages.mm("gui.tree.all_buyer_history_all.lore_spacer"));
                    lore.add(Messages.mm("gui.tree.all_buyer_history_all.lore_details_header"));
                    lore.addAll(details);
                }
            }

            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta m = paper.getItemMeta();
            m.displayName(titleComp);
            m.lore(lore);
            m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
            paper.setItemMeta(m);
            inv.setItem(idx, paper);
        }
    }

    /* ------------------------------- Items ---------------------------------- */

    private static String priceLabel(ItemStack price) {
        if (price == null || price.getType() == Material.AIR) return "—";
        String pretty = pretty(price.getType());
        int amt = Math.max(1, price.getAmount());
        return amt + " × " + pretty;
    }

    private static String pretty(Material m) {
        String s = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder out = new StringBuilder(s.length());
        boolean cap = true;
        for (char c : s.toCharArray()) {
            if (cap && Character.isLetter(c)) { out.append(Character.toUpperCase(c)); cap = false; }
            else out.append(c);
            if (c == ' ') cap = true;
        }
        return out.toString();
    }

    private static Component gray(String s) { return text(s, NamedTextColor.GRAY); }

    private static ItemStack pane() { ItemStack i=new ItemStack(Material.GRAY_STAINED_GLASS_PANE); ItemMeta m=i.getItemMeta(); m.displayName(Component.empty()); i.setItemMeta(m); return i; }

    /* ------------------------------------------------------------------------
     * "Back" button now uses messages.yml (name + multi-line lore).
     *   all_buyer_history_all.back_name
     *   all_buyer_history_all.back_lore (list)
     * --------------------------------------------------------------------- */
    private static ItemStack backToBuyerListButton() {
        ItemStack i = new ItemStack(Material.BARRIER);
        ItemMeta im = i.getItemMeta();
        im.displayName(Messages.mm("gui.tree.all_buyer_history_all.back_name"));
        im.lore(Messages.mmList("gui.tree.all_buyer_history_all.back_lore"));
        im.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        i.setItemMeta(im);
        return i;
    }

    private static ItemStack arrow(PotionType type) {
        ItemStack i = new ItemStack(Material.TIPPED_ARROW);
        PotionMeta pm = (PotionMeta) i.getItemMeta();
        pm.setBasePotionType(type);
        pm.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        i.setItemMeta(pm);
        return i;
    }

    private static void named(ItemStack i, Component name, List<Component> lore) {
        ItemMeta im = i.getItemMeta();
        im.displayName(name);
        if (lore != null && !lore.isEmpty()) im.lore(lore);
        i.setItemMeta(im);
    }

    /* ----------------------- Purchased Item Introspection ---------------------- */

    /**
     * Attempt to obtain the purchased ItemStack from the TransactionRecord via reflection.
     * Tries common method names; falls back to empty if not present in this build.
     */
    private static Optional<ItemStack> resolvePurchasedStack(TransactionRecord r) {
        String[] candidates = {"purchased", "purchasedItem", "item", "itemStack", "bought", "boughtItem", "stack", "product"};
        for (String name : candidates) {
            try {
                Method m = r.getClass().getMethod(name);
                Object o = m.invoke(r);
                if (o instanceof ItemStack is && is.getType() != Material.AIR) {
                    return Optional.of(is.clone());
                }
            } catch (Throwable ignored) {}
        }
        return Optional.empty();
    }

    /** Extra lore lines: custom name (if distinct), enchantments, and container contents. */
    private static List<Component> extraLoreFor(ItemStack stack) {
        List<Component> out = new ArrayList<>(8);
        ItemMeta meta = stack.getItemMeta();

        // Show custom name explicitly if it’s different from the material pretty name
        String custom = customName(meta);
        if (custom != null) {
            String base = pretty(stack.getType());
            if (!custom.equalsIgnoreCase(base)) {
                out.add(gray("Name: " + custom));
            }
        }

        // Enchantments (applied)
        if (meta != null && !meta.getEnchants().isEmpty()) {
            List<String> parts = new ArrayList<>(meta.getEnchants().size());
            for (Map.Entry<Enchantment, Integer> e : meta.getEnchants().entrySet()) {
                parts.add(prettyEnchant(e.getKey()) + " " + roman(e.getValue()));
            }
            out.add(gray("Enchants: " + String.join(", ", parts)));
        }

        // NEW: Enchanted Book stored enchants
        if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta esm && !esm.getStoredEnchants().isEmpty()) {
            List<String> parts = new ArrayList<>(esm.getStoredEnchants().size());
            for (Map.Entry<Enchantment, Integer> e : esm.getStoredEnchants().entrySet()) {
                parts.add(prettyEnchant(e.getKey()) + " " + roman(e.getValue()));
            }
            out.add(gray("Book Enchants: " + String.join(", ", parts)));
        }

        // NEW: Potion details (base potion + custom effects) for Potion/Splash/Lingering/Tipped Arrow
        if (meta instanceof org.bukkit.inventory.meta.PotionMeta pm) {
            // Base potion (guard for older APIs)
            try {
                org.bukkit.potion.PotionType base = pm.getBasePotionType();
                if (base != null) {
                    String kind = switch (stack.getType()) {
                        case SPLASH_POTION -> "Splash";
                        case LINGERING_POTION -> "Lingering";
                        case TIPPED_ARROW -> "Arrow";
                        default -> "Potion";
                    };
                    out.add(gray(kind + ": " + capWords(base.name().toLowerCase(Locale.ROOT).replace('_',' '))));
                }
            } catch (Throwable ignored) {}

            // Custom effects
            var effects = pm.getCustomEffects();
            if (!effects.isEmpty()) {
                out.add(gray("Effects:"));
                for (var fx : effects) {
                    String key = fx.getType().getKey().getKey(); // modern, not deprecated
                    String name = capWords(key.replace('_', ' '));
                    int level = Math.max(1, fx.getAmplifier() + 1);
                    int secs = Math.max(0, fx.getDuration() / 20);
                    out.add(gray(" • " + name + " " + roman(level) + " (" + secs + "s)"));
                }
            }
        }

        // Container contents (Shulker boxes / Bundles)
        out.addAll(summarizeContainerContents(stack));

        return out;
    }

    /** Name preference: custom display name → translated pretty material name. */
    private static String stackName(ItemStack s) {
        String custom = (s.hasItemMeta() ? customName(s.getItemMeta()) : null);
        return (custom != null ? custom : pretty(s.getType()));
    }

    /** Extract plain custom name from Adventure displayName if present. */
    private static String customName(ItemMeta meta) {
        if (meta == null) return null;
        try {
            Component c = meta.displayName();
            if (c == null) return null;
            String plain = PlainTextComponentSerializer.plainText().serialize(c).trim();
            return plain.isEmpty() ? null : plain;
        } catch (Throwable t) {
            // Fallback for older APIs (legacy String API); avoid null-check (often @NotNull in modern builds)
            try {
                @SuppressWarnings("deprecation")
                String legacy = meta.hasDisplayName() ? meta.getDisplayName() : "";
                return legacy.isBlank() ? null : legacy;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    /** Pretty-print enchantment keys, e.g., "sharpness" → "Sharpness". */
    private static String prettyEnchant(Enchantment e) {
        try {
            NamespacedKey k = e.getKey();
            return capWords(k.getKey().replace('_', ' '));
        } catch (Throwable ignored) {
            return "Enchantment";
        }
    }

    private static String capWords(String in) {
        StringBuilder out = new StringBuilder(in.length());
        boolean cap = true;
        for (char c : in.toCharArray()) {
            if (cap && Character.isLetter(c)) { out.append(Character.toUpperCase(c)); cap = false; }
            else out.append(c);
            if (c == ' ') cap = true;
        }
        return out.toString();
    }

    /** Roman numerals for levels (I..X etc.). */
    private static String roman(int n) {
        String[] M = {"", "M", "MM", "MMM"};
        String[] C = {"", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"};
        String[] X = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
        String[] I = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        return M[(n/1000)%10] + C[(n/100)%10] + X[(n/10)%10] + I[n%10];
    }

    /** Summarize contents for shulker boxes and bundles (limit lines for lore). */
    private static List<Component> summarizeContainerContents(ItemStack s) {
        List<Component> out = new ArrayList<>();
        ItemMeta meta = s.getItemMeta();
        if (meta instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox box) {
            ItemStack[] contents = box.getInventory().getContents();
            List<String> lines = describeContents(Arrays.asList(contents));
            if (!lines.isEmpty()) {
                out.add(gray("Contents:"));
                for (String line : lines) out.add(gray(" • " + line));
            }
        } else if (isBundleMeta(meta)) { // no direct dependency on experimental API
            List<ItemStack> items = bundleItems(meta);
            List<String> lines = describeContents(items);
            if (!lines.isEmpty()) {
                out.add(gray("Bundle:"));
                for (String line : lines) out.add(gray(" • " + line));
            }
        }
        return out;
    }

    /**
     * Turn a list of stacks into compact lines like "3× Diamond Sword (Sharpness V)".
     * (Display strings; these lines are appended as plain gray Components above.)
     */
    private static final int MAX_CONTENT_LINES = 6;

    private static List<String> describeContents(Collection<ItemStack> items) {
        List<String> lines = new ArrayList<>();
        int shown = 0, skipped = 0;
        for (ItemStack it : items) {
            if (it == null || it.getType() == Material.AIR) continue;
            String name = stackName(it);
            String ench = "";
            if (it.hasItemMeta() && !it.getItemMeta().getEnchants().isEmpty()) {
                List<String> parts = new ArrayList<>();
                for (Map.Entry<Enchantment, Integer> e : it.getItemMeta().getEnchants().entrySet()) {
                    parts.add(prettyEnchant(e.getKey()) + " " + roman(e.getValue()));
                }
                ench = " (" + String.join(", ", parts) + ")";
            }
            String line = Math.max(1, it.getAmount()) + "× " + name + ench;
            if (shown < MAX_CONTENT_LINES) {
                lines.add(line);
                shown++;
            } else {
                skipped++;
            }
        }
        if (skipped > 0) lines.add("…and " + skipped + " more");
        return lines;
    }

    /**
     * True if this ItemMeta behaves like a Bundle:
     * We detect by capability, not by exact class name (CraftBukkit uses impl classes).
     * Avoids depending on @ApiStatus.Experimental interfaces directly.
     */
    private static boolean isBundleMeta(ItemMeta meta) {
        if (meta == null) return false;
        try {
            // BundleMeta exposes a no-arg getItems(): List<ItemStack>
            meta.getClass().getMethod("getItems");
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    /** Reflective access to BundleMeta#getItems to avoid @ApiStatus.Experimental warnings. */
    @SuppressWarnings({"unchecked"})
    private static List<ItemStack> bundleItems(ItemMeta meta) {
        try {
            Method m = meta.getClass().getMethod("getItems");
            Object o = m.invoke(meta);
            if (o instanceof List) return (List<ItemStack>) o;
        } catch (Throwable ignored) {}
        return Collections.emptyList();
    }
}
