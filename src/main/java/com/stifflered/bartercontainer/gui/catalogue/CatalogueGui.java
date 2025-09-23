package com.stifflered.bartercontainer.gui.catalogue;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.gui.common.SimplePaginator;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.stifflered.bartercontainer.util.Messages;
import com.stifflered.bartercontainer.util.TrackingManager;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Catalogue GUI:
 * - Aggregates sale items from all BarterStores on the server.
 * - Displays a paginated chest GUI of unique items being sold.
 * - Clicking an item begins tracking to the nearest shop location that sells it.

 * Search:
 * - Fuzzy matching against material + custom name + potion effects (incl. 1.21+ Potion Contents) + enchantments.
 * - Also searches inside shulker boxes and bundles (via reflection), recursively.

 * UX:
 * - Bottom-right search button opens a one-shot text prompt (Java chat / Bedrock form→sign→anvil fallback).
 * - All clicks are cancelled except our handlers.
 */
public class CatalogueGui extends ChestGui {

    private static NamespacedKey searchMarkerKey() {
        return new NamespacedKey(BarterContainer.INSTANCE, "catalog_search_button");
    }

    public CatalogueGui() {
        super(6, ComponentHolder.of(Messages.mm("gui.catalogue.title")));
        this.setOnGlobalClick(event -> event.setCancelled(true));
        SearchChat.ensureInstalled();
        SearchClick.ensureInstalled();
    }

    @Override
    public void show(@NotNull HumanEntity humanEntity) {
        super.show(humanEntity);
        buildAndOpen((Player) humanEntity, null);
    }

    private void buildAndOpen(Player viewer, String filterQuery) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Map<ItemStack, List<BarterStore>> sellRegistry = new HashMap<>();
                List<BarterStore> containers = BarterManager.INSTANCE.getAll();

                for (BarterStore store : containers) {
                    for (ItemStack itemStack : store.getSaleStorage().getContents()) {
                        if (itemStack != null && !itemStack.isEmpty()) {
                            ItemStack checkItem = itemStack.clone();
                            checkItem.setAmount(1);

                            if (filterQuery != null && !matchesQuery(checkItem, filterQuery)) {
                                continue;
                            }

                            List<BarterStore> storesForItem =
                                    sellRegistry.computeIfAbsent(checkItem, k -> new ArrayList<>());

                            boolean shouldRegister = true;
                            for (BarterStore oldStore : storesForItem) {
                                if (oldStore.getKey().equals(store.getKey())) {
                                    shouldRegister = false;
                                    break;
                                }
                            }
                            if (shouldRegister) {
                                storesForItem.add(store);
                            }
                        }
                    }
                }

                List<GuiItem> items = new ArrayList<>();
                for (Map.Entry<ItemStack, List<BarterStore>> entry : sellRegistry.entrySet()) {
                    items.add(formatItem(entry.getKey().clone(), entry.getValue()));
                }

                items.sort(Comparator.comparing(o -> o.getItem().getType().key().toString()));

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (filterQuery != null && items.isEmpty()) {
                            viewer.sendMessage(Messages.mm("gui.catalogue.search.no_results", "query", filterQuery));
                            buildAndOpen(viewer, null);
                            return;
                        }

                        ItemStack searchButton = buildSearchButton();

                        new SimplePaginator(
                                6,
                                ComponentHolder.of(Messages.mm("gui.catalogue.title")),
                                items,
                                null,
                                searchButton
                        ).show(viewer);
                    }
                }.runTask(BarterContainer.INSTANCE);
            }
        }.runTaskAsynchronously(BarterContainer.INSTANCE);
    }

    protected GuiItem formatItem(ItemStack baseItem, List<BarterStore> items) {
        ItemUtil.wrapEdit(baseItem, (meta) -> {
            List<Component> lore = new ArrayList<>(items.size());
            lore.addAll(Messages.mmList("gui.catalogue.item_lore"));
            lore.add(Component.empty());

            for (BarterStore store : items) {
                if (!store.getLocations().isEmpty()) {
                    Location location = store.getLocations().get(0);
                    String text = location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
                    lore.add(Component.text("~ " + text, NamedTextColor.GREEN));
                }
            }

            Components.lore(meta, lore);
        });

        return new GuiItem(baseItem, clickEvent -> {
            Location reference = clickEvent.getWhoClicked().getLocation();

            Location closestLocation = null;
            double smallestDistance = Double.MAX_VALUE;

            for (BarterStore store : items) {
                if (!store.getLocations().isEmpty()) {
                    for (Location location : store.getLocations()) {
                        double distance = location.distance(reference);
                        if (distance < smallestDistance) {
                            smallestDistance = distance;
                            closestLocation = location;
                        }
                    }
                }
            }

            if (closestLocation != null) {
                TrackingManager.instance().track((Player) clickEvent.getWhoClicked(), closestLocation);
            }
        });
    }

    private ItemStack buildSearchButton() {
        ItemStack button = BarterContainer.INSTANCE.getConfiguration().getCatalogueSearchItem();

        if (button == null || button.getType() == Material.AIR || button.getType() == Material.BARRIER) {
            button = new ItemStack(Material.COMPASS, 1);
        }

        ItemUtil.wrapEdit(button, meta -> {
            Components.name(meta, Messages.mm("gui.catalogue.search.button_name"));
            Components.lore(meta, Messages.mmList("gui.catalogue.search.button_lore"));
            meta.getPersistentDataContainer().set(searchMarkerKey(), PersistentDataType.BYTE, (byte) 1);
        });

        return button;
    }

    // ========================= SEARCH HELPERS =========================

    private static boolean matchesQuery(ItemStack stack, String rawQuery) {
        String q = normalize(rawQuery);
        if (q.isEmpty()) return true;

        String target = buildSearchText(stack);
        if (fuzzyContains(target, q)) return true;

        return containsMatchingItem(stack, q, 2);
    }

    private static String buildSearchText(ItemStack item) {
        String materialText = normalize(item.getType().key().toString());
        String display = normalize(plainName(item));
        String effects = extractEffectKeywords(item);
        String enchants = extractEnchantmentKeywords(item);

        String combined = display.isEmpty() ? materialText : (materialText + " " + display);
        if (!effects.isBlank()) combined += " " + effects;
        if (!enchants.isBlank()) combined += " " + enchants;
        combined += " " + materialAliases(materialText);

        return combined.trim().replaceAll("\\s+", " ");
    }

    private static String materialAliases(String materialText) {
        if (materialText.contains("copper")) return "copper copper block ingot raw cut oxidized weathered exposed waxed";
        if (materialText.contains("amethyst")) return "amethyst shard bud cluster";
        if (materialText.contains("netherite")) return "netherite ancient debris scrap";
        if (materialText.contains("quartz")) return "quartz block smooth pillar";
        return "";
    }

    // ----- POTIONS & EFFECTS (hardened for 1.20.x → 1.21+ with extra fallbacks) -----
    private static String extractEffectKeywords(ItemStack item) {
        if (!item.hasItemMeta()) return "";
        var meta = item.getItemMeta();
        StringBuilder sb = new StringBuilder();

        // 1) PotionMeta (potions, splash/lingering, tipped arrows)
        if (meta instanceof PotionMeta pm) {
            boolean gotAnything = false;

            // --- A) 1.21+ Potion Contents via reflection (preferred) ---
            try {
                Object pc = null;
                try {
                    Method hasPc = pm.getClass().getMethod("hasPotionContents");
                    if ((boolean) hasPc.invoke(pm)) {
                        Method getPc = pm.getClass().getMethod("getPotionContents");
                        pc = getPc.invoke(pm);
                    }
                } catch (NoSuchMethodException ignored) {
                    try {
                        Method getPc = pm.getClass().getMethod("potionContents");
                        pc = getPc.invoke(pm);
                    } catch (Throwable ignored2) {}
                }

                if (pc != null) {
                    String base = tryReadPotionType(pc);
                    if (base != null) { appendPotionTokens(sb, base); gotAnything = true; }

                    Collection<?> effects = tryReadCollection(pc, "customEffects", "getCustomEffects");
                    if (effects != null) {
                        for (Object o : effects) {
                            PotionEffectType type = null;
                            if (o instanceof PotionEffect pe) {
                                type = pe.getType();
                            } else {
                                try {
                                    Object t = o.getClass().getMethod("getType").invoke(o);
                                    if (t instanceof PotionEffectType pet) type = pet;
                                } catch (Throwable ignored3) {}
                            }
                            if (type != null) { appendEffectTypeTokens(sb, type); gotAnything = true; }
                        }
                    }
                }
            } catch (Throwable ignored) { /* tolerate */ }

            // --- B) Directly on PotionMeta (Paper variants) ---
            if (!gotAnything) {
                try {
                    Object type = pm.getClass().getMethod("getPotionType").invoke(pm); // new-ish Paper path
                    if (type != null) {
                        appendPotionTokens(sb, reflectPotionTypeToString(type));
                        gotAnything = true;
                    }
                } catch (Throwable ignored) {}
            }

            // --- C) 1.20.x legacy API ---
            if (!gotAnything) {
                String base = null;
                try {
                    Object type = pm.getClass().getMethod("getBasePotionType").invoke(pm);
                    if (type != null) base = reflectPotionTypeToString(type);
                } catch (Throwable __) {
                    try {
                        Object data = pm.getClass().getMethod("getBasePotionData").invoke(pm);
                        if (data != null) {
                            Object t = data.getClass().getMethod("getType").invoke(data);
                            if (t != null) base = reflectPotionTypeToString(t);
                        }
                    } catch (Throwable ignored) {}
                }
                if (base != null) { appendPotionTokens(sb, base); gotAnything = true; }
                for (PotionEffect pe : pm.getCustomEffects()) { appendEffectTypeTokens(sb, pe.getType()); gotAnything = true; }
            }

            // --- D) Serialized meta map fallback (very robust across builds) ---
            if (!gotAnything) {
                String base = readPotionTypeFromMetaSerialize(item);
                if (base != null && !base.isBlank()) {
                    appendPotionTokens(sb, base);
                    gotAnything = true;
                }
            }

            // --- E) Final tiny hints & PDC fallback ---
            if (!gotAnything) {
                try {
                    String raw = meta.getPersistentDataContainer()
                            .get(NamespacedKey.minecraft("potion"), PersistentDataType.STRING);
                    if (raw != null && !raw.isBlank()) {
                        int colon = raw.indexOf(':');
                        String idPath = (colon >= 0) ? raw.substring(colon + 1) : raw;
                        appendPotionTokens(sb, idPath);
                        gotAnything = true;
                    }
                } catch (Throwable ignored) { /* tolerate */ }
            }
            if (!gotAnything) {
                if (item.getType() == Material.TIPPED_ARROW) sb.append("arrow ");
                if (item.getType().name().contains("POTION")) sb.append("potion ");
            }
        }

        // 2) Suspicious stew custom effects
        if (meta instanceof SuspiciousStewMeta ssm) {
            for (PotionEffect pe : ssm.getCustomEffects()) appendEffectTypeTokens(sb, pe.getType());
        }

        return sb.toString().trim();
    }

    // Try different method names to read the base potion from PotionContents
    private static String tryReadPotionType(Object potionContents) {
        String[] getters = {"potionType", "getPotionType", "potion", "getPotion"};
        for (String name : getters) {
            try {
                Method m = potionContents.getClass().getMethod(name);
                Object val = m.invoke(potionContents);
                if (val == null) continue;

                if (val instanceof Optional<?> opt) {
                    Object t = opt.orElse(null);
                    if (t != null) return reflectPotionTypeToString(t);
                    continue;
                }

                return reflectPotionTypeToString(val);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    // Convert a PotionType / namespaced object to a friendly string
    private static String reflectPotionTypeToString(Object potionTypeObj) {
        try {
            Method getKey = potionTypeObj.getClass().getMethod("getKey");
            Object keyObj = getKey.invoke(potionTypeObj);
            if (keyObj instanceof NamespacedKey nk) {
                return nk.getKey(); // e.g., "invisibility"
            }
            if (keyObj != null) {
                String s = String.valueOf(keyObj);
                int colon = s.indexOf(':');
                return (colon >= 0) ? s.substring(colon + 1) : s;
            }
        } catch (Throwable ignored) {
            // Fall through to toString/enum name
        }
        String s = String.valueOf(potionTypeObj);
        int colon = s.indexOf(':');
        return (colon >= 0) ? s.substring(colon + 1) : s;
    }

    // Try reading a Collection<?> via any of the provided method names
    private static Collection<?> tryReadCollection(Object obj, String... methodNames) {
        for (String n : methodNames) {
            try {
                Method m = obj.getClass().getMethod(n);
                Object v = m.invoke(obj);
                if (v instanceof Collection<?> c) return c;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static void appendPotionTokens(StringBuilder sb, String enumName) {
        String norm = normalize(enumName.replace('_', ' '));
        if (norm.isBlank()) return;

        sb.append(norm).append(' ');
        appendCommonEffectAliases(sb, norm);
    }

    private static void appendEffectTypeTokens(StringBuilder sb, PotionEffectType type) {
        if (type == null) return;
        String key = type.getKey().getKey(); // e.g. "invisibility"
        String norm = normalize(key);
        if (norm.isBlank()) return;

        sb.append(norm).append(' ');
        appendCommonEffectAliases(sb, norm);
    }

    // Wide coverage of synonyms/colloquialisms for potion/effect searches
    private static void appendCommonEffectAliases(StringBuilder sb, String norm) {
        // speed / slowness
        if (norm.contains("speed") || norm.contains("swiftness")) sb.append("speed swift swiftness fast ");
        if (norm.contains("slowness")) sb.append("slow ");

        // invisibility
        if (norm.contains("invis")) sb.append("invis invisible invisibility ");

        // night vision / blindness
        if (norm.contains("night vision")) sb.append("vision nightvision ");
        if (norm.contains("blind")) sb.append("blindness ");

        // water breathing / conduit power / dolphins grace
        if (norm.contains("water breathing")) sb.append("waterbreathing breathe underwater ");
        if (norm.contains("conduit")) sb.append("conduit power underwater ");
        if (norm.contains("dolphin")) sb.append("dolphins grace swim ");

        // fire resistance
        if (norm.contains("fire res")) sb.append("fire resistance fireresistant ");

        // jump boost / slow falling / levitation
        if (norm.contains("leap") || norm.contains("jump")) sb.append("jump boost leaping ");
        if (norm.contains("slow falling")) sb.append("slowfall feather ");
        if (norm.contains("levitation")) sb.append("levitate float ");

        // health effects
        if (norm.contains("heal") || norm.contains("instant health")) sb.append("heal health instanthealing ");
        if (norm.contains("regeneration") || norm.equals("regen")) sb.append("regen regeneration ");
        if (norm.contains("absorption")) sb.append("absorb hearts ");
        if (norm.contains("resistance")) sb.append("resist damage reduction ");
        if (norm.contains("saturation")) sb.append("saturated food ");

        // combat effects
        if (norm.contains("strength")) sb.append("strength strong ");
        if (norm.contains("weakness")) sb.append("weak weakens ");
        if (norm.contains("poison")) sb.append("poisoned ");
        if (norm.contains("harming") || norm.contains("instant damage")) sb.append("harm damage ");
        if (norm.contains("wither") || norm.contains("decay")) sb.append("wither decay ");

        // utility / mining
        if (norm.contains("haste")) sb.append("haste fast mine ");
        if (norm.contains("mining fatigue") || norm.contains("fatigue")) sb.append("fatigue slowmine ");

        // luck / turtle master
        if (norm.contains("luck")) sb.append("luck lucky ");
        if (norm.contains("turtle")) sb.append("turtle master slowness resistance ");
    }

    // ----- ENCHANTMENTS (version-proof) -----
    private static String extractEnchantmentKeywords(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) return "";
        var meta = item.getItemMeta();
        StringBuilder sb = new StringBuilder();

        if (meta != null && !meta.getEnchants().isEmpty()) {
            for (Map.Entry<Enchantment, Integer> e : meta.getEnchants().entrySet()) {
                appendEnchantmentTokens(sb, e.getKey(), e.getValue());
            }
        }

        if (meta instanceof EnchantmentStorageMeta esm) {
            var stored = esm.getStoredEnchants(); // guaranteed non-null
            if (!stored.isEmpty()) {
                for (Map.Entry<Enchantment, Integer> e : stored.entrySet()) {
                    appendEnchantmentTokens(sb, e.getKey(), e.getValue());
                }
            }
        }
        return sb.toString().trim();
    }

    private static void appendEnchantmentTokens(StringBuilder sb, Enchantment ench, int level) {
        if (ench == null) return;
        String path = ench.getKey().getKey();     // e.g. "feather_falling"
        String norm = normalize(path);            // "feather falling"
        String tight = norm.replace(" ", "");     // "featherfalling"
        String initials = initialsOf(norm);       // only 2–3 letters or ""

        if (!norm.isBlank())  sb.append(norm).append(' ');
        if (!tight.isBlank()) sb.append(tight).append(' ');
        if (!initials.isBlank()) sb.append(initials).append(' ');  // won't be 1 letter

        if (level > 0) {
            sb.append(level).append(' ');
            String roman = toRoman(level);
            if (!roman.isEmpty()) sb.append(roman.toLowerCase(Locale.ROOT)).append(' ');
        }
    }

    // --- Make initials at least 2 letters (and at most 3) ---
    private static String initialsOf(String spaced) {
        String[] parts = spaced.split(" ");
        StringBuilder b = new StringBuilder();
        for (String p : parts) if (!p.isBlank()) b.append(p.charAt(0));
        String s = b.toString();
        return (s.length() >= 2 && s.length() <= 3) ? s : "";
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> "";
        };
    }

    // ----- CONTAINER SCAN -----
    private static boolean containsMatchingItem(ItemStack stack, String qNorm, int depth) {
        if (stack == null || stack.isEmpty() || depth < 0) return false;

        // Bundle via reflection (avoid experimental dependency)
        try {
            if (stack.hasItemMeta()) {
                Object meta = stack.getItemMeta();
                if (meta != null) {
                    Class<?> bundleMetaClass = Class.forName("org.bukkit.inventory.meta.BundleMeta");
                    if (bundleMetaClass.isInstance(meta)) {
                        Method getItems = bundleMetaClass.getMethod("getItems");
                        Object result = getItems.invoke(meta);
                        if (result instanceof List<?> list) {
                            for (Object obj : list) {
                                if (!(obj instanceof ItemStack inner) || inner.isEmpty()) continue;
                                String innerText = buildSearchText(inner);
                                if (fuzzyContains(innerText, qNorm)) return true;
                                if (containsMatchingItem(inner, qNorm, depth - 1)) return true;
                            }
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }

        // Shulker contents
        if (stack.hasItemMeta() && stack.getItemMeta() instanceof BlockStateMeta bsm
                && bsm.getBlockState() instanceof ShulkerBox box) {
            for (ItemStack inner : box.getInventory().getContents()) {
                if (inner == null || inner.isEmpty()) continue;
                String innerText = buildSearchText(inner);
                if (fuzzyContains(innerText, qNorm)) return true;
                if (containsMatchingItem(inner, qNorm, depth - 1)) return true;
            }
        }

        return false;
    }

    // ----- UTILS -----
    private static String plainName(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) return "";
        var meta = stack.getItemMeta();
        if (meta == null) return "";
        Component name = meta.displayName();
        if (name == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(name);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("[^a-z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean fuzzyContains(String targetNorm, String queryNorm) {
        if (targetNorm.contains(queryNorm)) return true;

        String[] targetTokens = targetNorm.split(" ");
        String[] queryTokens  = queryNorm.split(" ");

        for (String q : queryTokens) {
            boolean matched = false;
            for (String t : targetTokens) {
                if (t.isEmpty()) continue;

                // 1) exact token match always OK
                if (t.equals(q)) { matched = true; break; }

                // 2) short queries (<=3) must match a whole token, not substring
                if (q.length() <= 3) continue;

                // 3) only allow targetToken to contain the query (not the reverse)
                if (t.length() >= 3 && t.contains(q)) { matched = true; break; }

                // 4) edit distance as a fallback for typos
                int d = damerauLevenshtein(t, q);
                int maxLen = Math.max(t.length(), q.length());
                int tol = Math.max(1, (int) Math.floor(maxLen * 0.34));
                if (d <= tol) { matched = true; break; }
            }
            if (!matched) return false;
        }
        return true;
    }

    private static int damerauLevenshtein(String a, String b) {
        int n = a.length(), m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) dp[i][0] = i;
        for (int j = 0; j <= m; j++) dp[0][j] = j;
        for (int i = 1; i <= n; i++) {
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                char cb = b.charAt(j - 1);
                int cost = (ca == cb) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
                if (i > 1 && j > 1 && ca == b.charAt(j - 2) && a.charAt(i - 2) == cb) {
                    dp[i][j] = Math.min(dp[i][j], dp[i - 2][j - 2] + 1);
                }
            }
        }
        return dp[n][m];
    }

    // ========================= INPUT FLOWS =========================

    private static final class SearchChat implements Listener {
        private static final Map<UUID, java.util.function.Consumer<String>> PENDING = new ConcurrentHashMap<>();
        private static volatile boolean installed = false;

        private SearchChat() {}

        static void ensureInstalled() {
            if (!installed) {
                synchronized (SearchChat.class) {
                    if (!installed) {
                        Bukkit.getPluginManager().registerEvents(new SearchChat(), BarterContainer.INSTANCE);
                        installed = true;
                    }
                }
            }
        }

        static void register(Player p, java.util.function.Consumer<String> callback) {
            ensureInstalled();
            PENDING.put(p.getUniqueId(), callback);
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onPaperChat(AsyncChatEvent e) {
            final Player player = e.getPlayer();
            final var cb = PENDING.remove(player.getUniqueId());
            if (cb == null) return;

            final String msg = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();

            e.setCancelled(true);
            try { e.viewers().clear(); } catch (Throwable ignored) {}
            try { e.renderer((source, displayName, message, viewer) -> Component.empty()); } catch (Throwable ignored) {}

            Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> {
                if (msg.equalsIgnoreCase("cancel") || msg.isBlank()) {
                    player.sendMessage(Messages.mm("gui.catalogue.search.canceled"));
                    cb.accept(null);
                } else {
                    cb.accept(msg);
                }
            });
        }

        @EventHandler(priority = EventPriority.LOWEST)
        @SuppressWarnings("deprecation")
        public void onLegacyChat(org.bukkit.event.player.AsyncPlayerChatEvent e) {
            final Player player = e.getPlayer();
            final var cb = PENDING.remove(player.getUniqueId());
            if (cb == null) return;

            final String msg = e.getMessage().trim();

            e.setCancelled(true);
            try { e.getRecipients().clear(); } catch (Throwable ignored) {}

            Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> {
                if (msg.equalsIgnoreCase("cancel") || msg.isBlank()) {
                    player.sendMessage(Messages.mm("gui.catalogue.search.canceled"));
                    cb.accept(null);
                } else {
                    cb.accept(msg);
                }
            });
        }
    }

    private static final class SearchClick implements Listener {
        private static volatile boolean installed = false;

        static void ensureInstalled() {
            if (!installed) {
                synchronized (SearchClick.class) {
                    if (!installed) {
                        Bukkit.getPluginManager().registerEvents(new SearchClick(), BarterContainer.INSTANCE);
                        installed = true;
                    }
                }
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onClick(InventoryClickEvent e) {
            if (!(e.getWhoClicked() instanceof Player player)) return;

            if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;

            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.isEmpty() || !clicked.hasItemMeta()) return;

            var meta = clicked.getItemMeta();
            Byte marker = meta.getPersistentDataContainer().get(searchMarkerKey(), PersistentDataType.BYTE);
            if (marker == null || marker != (byte) 1) return;

            e.setCancelled(true);
            player.closeInventory();

            if (isBedrock(player)) {
                BedrockFormPrompt.open(player, (query) -> {
                    if (query == null || query.isBlank()) {
                        new CatalogueGui().buildAndOpen(player, null);
                    } else {
                        player.sendMessage(Messages.mm("gui.catalogue.search.working", "query", query));
                        new CatalogueGui().buildAndOpen(player, query);
                    }
                });
                return;
            }

            Bukkit.getScheduler().runTaskLater(BarterContainer.INSTANCE, () -> {
                SearchChat.register(player, (query) -> {
                    if (query == null) {
                        new CatalogueGui().buildAndOpen(player, null);
                    } else {
                        player.sendMessage(Messages.mm("gui.catalogue.search.working", "query", query));
                        new CatalogueGui().buildAndOpen(player, query);
                    }
                });
                player.sendMessage(Messages.mm("gui.catalogue.search.prompt"));
            }, 2L);
        }

        private static boolean isBedrock(Player player) {
            try {
                Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Object instance = api.getMethod("getInstance").invoke(null);
                return (boolean) api.getMethod("isFloodgatePlayer", java.util.UUID.class)
                        .invoke(instance, player.getUniqueId());
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    private static final class BedrockFormPrompt {
        static void open(Player bukkitPlayer, java.util.function.Consumer<String> callback) {
            Runnable bedrockSignFallback = () ->
                    Bukkit.getScheduler().runTask(BarterContainer.INSTANCE,
                            () -> SignPrompt.open(bukkitPlayer, callback));

            Runnable anvilFallback = () ->
                    Bukkit.getScheduler().runTask(BarterContainer.INSTANCE,
                            () -> AnvilPrompt.open(bukkitPlayer, callback));

            try {
                Class<?> apiCls = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Object api = apiCls.getMethod("getInstance").invoke(null);

                boolean isFg = (boolean) apiCls.getMethod("isFloodgatePlayer", java.util.UUID.class)
                        .invoke(api, bukkitPlayer.getUniqueId());
                if (!isFg) { try { bedrockSignFallback.run(); } catch (Throwable __) { anvilFallback.run(); } return; }

                Object fgPlayer = apiCls.getMethod("getPlayer", java.util.UUID.class)
                        .invoke(api, bukkitPlayer.getUniqueId());
                if (fgPlayer == null) { try { bedrockSignFallback.run(); } catch (Throwable __) { anvilFallback.run(); } return; }

                String title       = fallbackPlain("gui.catalogue.search.bedrock_title", "Catalogue Search");
                String label       = fallbackPlain("gui.catalogue.search.bedrock_label", "Enter your search:");
                String placeholder = fallbackPlain("gui.catalogue.search.bedrock_placeholder", "type here…");

                Class<?> CustomFormCls         = Class.forName("org.geysermc.floodgate.api.form.CustomForm");
                Class<?> BuilderCls            = Class.forName("org.geysermc.floodgate.api.form.CustomForm$Builder");
                Class<?> FormCls               = Class.forName("org.geysermc.floodgate.api.form.Form");
                Class<?> FgPlayerCls           = Class.forName("org.geysermc.floodgate.api.player.FloodgatePlayer");
                Class<?> ResponseCls           = Class.forName("org.geysermc.floodgate.api.form.response.FormResponse");
                Class<?> CustomFormResponseCls = Class.forName("org.geysermc.floodgate.api.form.response.CustomFormResponse");
                Class<?> FormElementCls        = Class.forName("org.geysermc.floodgate.api.form.element.FormElement");
                Class<?> InputCls              = Class.forName("org.geysermc.floodgate.api.form.element.Input");

                Object builder = CustomFormCls.getMethod("builder").invoke(null);
                builder = BuilderCls.getMethod("title", String.class).invoke(builder, title);

                boolean inputAdded = false;
                try {
                    builder = BuilderCls.getMethod("input", String.class, String.class, String.class)
                            .invoke(builder, label, placeholder, "");
                    inputAdded = true;
                } catch (Throwable ignored) {}
                if (!inputAdded) {
                    try {
                        builder = BuilderCls.getMethod("input", String.class, String.class)
                                .invoke(builder, label, placeholder);
                        inputAdded = true;
                    } catch (Throwable ignored) {}
                }
                if (!inputAdded) {
                    try {
                        builder = BuilderCls.getMethod("input", String.class)
                                .invoke(builder, label);
                        inputAdded = true;
                    } catch (Throwable ignored) {}
                }
                if (!inputAdded) {
                    Object input;
                    try {
                        input = InputCls.getConstructor(String.class, String.class, String.class)
                                .newInstance(label, placeholder, "");
                    } catch (Throwable e1) {
                        try {
                            input = InputCls.getConstructor(String.class, String.class)
                                    .newInstance(label, placeholder);
                        } catch (Throwable e2) {
                            input = InputCls.getConstructor(String.class).newInstance(label);
                        }
                    }
                    builder = BuilderCls.getMethod("addElement", FormElementCls).invoke(builder, input);
                }

                boolean handlerBound = false;
                try {
                    java.util.function.BiConsumer<Object, Object> bi = (p, respObj) -> {
                        boolean closed = true;
                        try { closed = (boolean) ResponseCls.getMethod("isClosed").invoke(respObj); } catch (Throwable ignoredX) {}
                        if (respObj == null || closed) {
                            Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> callback.accept(null));
                            return;
                        }
                        String text = null;
                        try { text = (String) CustomFormResponseCls.getMethod("getInput", int.class).invoke(respObj, 0); } catch (Throwable ignoredY) {}
                        final String val = (text == null) ? "" : text.trim();
                        Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> callback.accept(val));
                    };
                    builder = BuilderCls.getMethod("responseHandler", java.util.function.BiConsumer.class).invoke(builder, bi);
                    handlerBound = true;
                } catch (Throwable ignored) {
                    try {
                        java.util.function.Consumer<Object> cons = (respObj) -> {
                            boolean closed = true;
                            try { closed = (boolean) ResponseCls.getMethod("isClosed").invoke(respObj); } catch (Throwable ignoredX) {}
                            if (respObj == null || closed) {
                                Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> callback.accept(null));
                                return;
                            }
                            String text = null;
                            try { text = (String) CustomFormResponseCls.getMethod("getInput", int.class).invoke(respObj, 0); } catch (Throwable ignoredY) {}
                            final String val = (text == null) ? "" : text.trim();
                            Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> callback.accept(val));
                        };
                        builder = BuilderCls.getMethod("responseHandler", java.util.function.Consumer.class).invoke(builder, cons);
                        handlerBound = true;
                    } catch (Throwable ignored2) { /* keep false */ }
                }
                if (!handlerBound) { try { bedrockSignFallback.run(); } catch (Throwable __) { anvilFallback.run(); } return; }

                Object form = BuilderCls.getMethod("build").invoke(builder);

                Bukkit.getScheduler().runTaskLater(BarterContainer.INSTANCE, () -> {
                    try {
                        try {
                            FgPlayerCls.getMethod("sendForm", FormCls).invoke(fgPlayer, form);
                        } catch (Throwable trySendTo) {
                            Method mSendTo = null;
                            for (Method m : FormCls.getMethods()) {
                                if (m.getName().equals("sendTo") && m.getParameterCount() == 1) {
                                    mSendTo = m;
                                    break;
                                }
                            }
                            if (mSendTo != null) {
                                mSendTo.invoke(form, fgPlayer);
                            } else {
                                throw new NoSuchMethodException("No suitable Form#sendTo(..) method found");
                            }
                        }
                    } catch (Throwable sendFail) {
                        try { bedrockSignFallback.run(); } catch (Throwable __) { anvilFallback.run(); }
                    }
                }, 2L);

            } catch (Throwable t) {
                try { bedrockSignFallback.run(); } catch (Throwable __) { anvilFallback.run(); }
            }
        }

        private static String fallbackPlain(String msgKey, String def) {
            String s = Messages.plain(Messages.mm(msgKey));
            return s.isBlank() ? def : s;
        }
    }

    private static final class SignPrompt implements Listener {
        private static volatile boolean installed = false;

        private static final Map<UUID, java.util.function.Consumer<String>> WAITING = new ConcurrentHashMap<>();
        private static final Map<UUID, BlockData> PREV_DATA = new ConcurrentHashMap<>();
        private static final Map<UUID, TileState> PREV_STATE = new ConcurrentHashMap<>();
        private static final Map<UUID, Location> SIGN_LOC = new ConcurrentHashMap<>();

        static void ensureInstalled() {
            if (!installed) {
                synchronized (SignPrompt.class) {
                    if (!installed) {
                        Bukkit.getPluginManager().registerEvents(new SignPrompt(), BarterContainer.INSTANCE);
                        installed = true;
                    }
                }
            }
        }

        static void open(Player p, java.util.function.Consumer<String> cb) {
            ensureInstalled();

            Location loc = p.getLocation().clone().add(0, 2, 0).toBlockLocation();

            Block b = loc.getBlock();
            PREV_DATA.put(p.getUniqueId(), b.getBlockData());
            if (b.getState() instanceof TileState ts) PREV_STATE.put(p.getUniqueId(), ts);

            b.setType(Material.OAK_SIGN, false);
            Sign sign = (Sign) b.getState();
            sign.update(false, false);

            WAITING.put(p.getUniqueId(), cb);
            SIGN_LOC.put(p.getUniqueId(), loc);

            try {
                p.openSign(sign, Side.FRONT);
            } catch (Throwable ignored) {
                Bukkit.getScheduler().runTaskLater(BarterContainer.INSTANCE, () -> {
                    try { p.openSign(sign, Side.FRONT); } catch (Throwable t) { throw new RuntimeException(t); }
                }, 2L);
            }
        }

        @EventHandler
        public void onSignChange(SignChangeEvent e) {
            Player p = e.getPlayer();
            var cb = WAITING.remove(p.getUniqueId());
            if (cb == null) return;

            List<Component> comps;
            try {
                comps = e.lines();
            } catch (Throwable __) {
                List<Component> tmp = new ArrayList<>(4);
                try {
                    Method m = e.getClass().getMethod("getLine", int.class);
                    for (int i = 0; i < 4; i++) {
                        Object val = m.invoke(e, i);
                        String s = (val == null) ? "" : val.toString();
                        tmp.add(Component.text(s));
                    }
                } catch (Throwable ___) {
                    tmp.add(Component.empty());
                    tmp.add(Component.empty());
                    tmp.add(Component.empty());
                    tmp.add(Component.empty());
                }
                comps = tmp;
            }

            StringBuilder sb = new StringBuilder();
            PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
            for (Component c : comps) {
                String s = (c == null) ? "" : plain.serialize(c).trim();
                if (!s.isEmpty()) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(s);
                }
            }
            String text = sb.toString().trim();

            if (text.equalsIgnoreCase("cancel") || text.isBlank()) {
                Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> cb.accept(null));
            } else {
                Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> cb.accept(text));
            }

            restoreSignBlock(p.getUniqueId());
        }

        @EventHandler
        public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
            UUID id = e.getPlayer().getUniqueId();
            if (WAITING.remove(id) != null) restoreSignBlock(id);
        }

        private static void restoreSignBlock(UUID id) {
            Location loc = SIGN_LOC.remove(id);
            if (loc == null) return;
            Block b = loc.getBlock();

            BlockData prev = PREV_DATA.remove(id);
            if (prev != null) b.setBlockData(prev, false);

            TileState oldState = PREV_STATE.remove(id);
            if (oldState != null) oldState.update(false, false);
        }
    }

    private static final class AnvilPrompt {

        private record XpSnapshot(int total, int level, float exp) {}

        static void open(Player player, java.util.function.Consumer<String> callback) {
            XpSnapshot snap = new XpSnapshot(player.getTotalExperience(), player.getLevel(), player.getExp());

            int tempLevel = Math.max(30, player.getLevel());
            player.setLevel(tempLevel);

            String title = Messages.plain(Messages.mm("gui.catalogue.search.anvil_title"));
            String initialText = Messages.plain(Messages.mm("gui.catalogue.search.anvil_placeholder"));

            ItemStack leftItem = new ItemStack(Material.WOODEN_SHOVEL);
            ItemUtil.wrapEdit(leftItem, meta -> {
                Components.name(meta, Messages.mm("gui.catalogue.search.anvil_paper_name"));
                Components.lore(meta, Messages.mmList("gui.catalogue.search.anvil_paper_lore"));
            });

            ItemStack output = new ItemStack(Material.WOODEN_SHOVEL);
            ItemUtil.wrapEdit(output, meta -> {
                Components.name(meta, Messages.mm("gui.catalogue.search.anvil_paper_name"));
                Components.lore(meta, Messages.mmList("gui.catalogue.search.anvil_paper_lore"));
            });

            java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean(false);

            try {
                net.wesjd.anvilgui.AnvilGUI.Builder builder =
                        new net.wesjd.anvilgui.AnvilGUI.Builder()
                                .plugin(BarterContainer.INSTANCE)
                                .title(title)
                                .text(initialText)
                                .itemLeft(leftItem)
                                .itemOutput(output);

                try { builder.getClass().getMethod("levelCost", int.class).invoke(builder, 0); } catch (Throwable ignored) {}

                builder.onClick((slot, state) -> {
                            if (slot == net.wesjd.anvilgui.AnvilGUI.Slot.INPUT_LEFT
                                    || slot == net.wesjd.anvilgui.AnvilGUI.Slot.INPUT_RIGHT) {
                                final String current = Objects.toString(state.getText(), "");
                                return List.of(
                                        net.wesjd.anvilgui.AnvilGUI.ResponseAction.run(() ->
                                                {
                                                    net.wesjd.anvilgui.AnvilGUI.Builder b2 =
                                                            new net.wesjd.anvilgui.AnvilGUI.Builder()
                                                                    .plugin(BarterContainer.INSTANCE)
                                                                    .title(title)
                                                                    .text(current)
                                                                    .itemLeft(leftItem)
                                                                    .itemOutput(output);
                                                    try { b2.getClass().getMethod("levelCost", int.class).invoke(b2, 0); } catch (Throwable ignored) {}
                                                    b2.onClick((s2, st2) -> {
                                                                if (s2 == net.wesjd.anvilgui.AnvilGUI.Slot.OUTPUT) {
                                                                    final String input2 = Objects.toString(st2.getText(), "").trim();
                                                                    if ("cancel".equalsIgnoreCase(input2)) {
                                                                        completed.set(true);
                                                                        Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> callback.accept(null));
                                                                        return List.of(net.wesjd.anvilgui.AnvilGUI.ResponseAction.close());
                                                                    }
                                                                    completed.set(true);
                                                                    Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> callback.accept(input2));
                                                                    return List.of(net.wesjd.anvilgui.AnvilGUI.ResponseAction.close());
                                                                }
                                                                return Collections.emptyList();
                                                            })
                                                            .onClose(st2 -> {
                                                                Player p2 = st2.getPlayer();
                                                                restoreXp(p2, snap);
                                                                if (!completed.get()) {
                                                                    Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> callback.accept(null));
                                                                }
                                                            })
                                                            .open(player);
                                                }
                                        ),
                                        net.wesjd.anvilgui.AnvilGUI.ResponseAction.close()
                                );
                            }

                            if (slot == net.wesjd.anvilgui.AnvilGUI.Slot.OUTPUT) {
                                final String input = Objects.toString(state.getText(), "").trim();

                                if ("cancel".equalsIgnoreCase(input)) {
                                    completed.set(true);
                                    return List.of(
                                            net.wesjd.anvilgui.AnvilGUI.ResponseAction.run(() ->
                                                    Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> callback.accept(null))
                                            ),
                                            net.wesjd.anvilgui.AnvilGUI.ResponseAction.close()
                                    );
                                }

                                completed.set(true);
                                return List.of(
                                        net.wesjd.anvilgui.AnvilGUI.ResponseAction.run(() ->
                                                Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> callback.accept(input))
                                        ),
                                        net.wesjd.anvilgui.AnvilGUI.ResponseAction.close()
                                );
                            }
                            return Collections.emptyList();
                        })
                        .onClose(state -> {
                            Player p = state.getPlayer();
                            restoreXp(p, snap);
                            if (!completed.get()) {
                                Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> callback.accept(null));
                            }
                        })
                        .open(player);
            } catch (Throwable t) {
                restoreXp(player, snap);
                Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> callback.accept(null));
            }
        }

        private static void restoreXp(Player p, XpSnapshot s) {
            try {
                p.setExp(0f);
                p.setLevel(0);
                p.setTotalExperience(0);
                p.setTotalExperience(s.total);
                p.setLevel(s.level);
                p.setExp(s.exp);
            } catch (Throwable ignored) {
                try {
                    p.setLevel(s.level);
                    p.setExp(s.exp);
                } catch (Throwable ignored2) {}
            }
        }
    }

    // Read potion type name from Bukkit's serialized meta map (handles keys like
    // "potion-type", "potion_type", "potion", "potion-type-id", etc.)
    private static String readPotionTypeFromMetaSerialize(ItemStack item) {
        try {
            var meta = item.getItemMeta();
            if (meta == null) return null;

            Map<String, Object> m = meta.serialize();
            if (m.isEmpty()) return null;

            // Look for any key that contains "potion"
            for (Map.Entry<String, Object> e : m.entrySet()) {
                String k = e.getKey();
                if (k == null) continue;
                k = k.toLowerCase(Locale.ROOT);
                if (!k.contains("potion")) continue;

                Object v = e.getValue();
                if (v == null) continue;

                // e.g. "INVISIBILITY" or "minecraft:invisibility"
                String s = String.valueOf(v).trim();
                if (s.isEmpty()) continue;

                // Strip quotes and namespace if present
                s = s.replace("\"", "");
                int colon = s.indexOf(':');
                return (colon >= 0) ? s.substring(colon + 1) : s;
            }
        } catch (Throwable ignored) {
            // tolerate weird meta impls
        }
        return null;
    }

    @SuppressWarnings("unused")
    private static List<GuiItem> getListedItems() {
        List<GuiItem> items = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.isItem()) {
                items.add(new GuiItem(new ItemStack(material)));
            }
        }
        return items;
    }
}
