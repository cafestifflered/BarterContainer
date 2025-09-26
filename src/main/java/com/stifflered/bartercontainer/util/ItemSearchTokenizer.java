package com.stifflered.bartercontainer.util;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.PotionContents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.Map;

// TODO: This needs to be cleaned up.
public class ItemSearchTokenizer {

    public static String buildSearchText(ItemStack item) {
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


    private static String extractEffectKeywords(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();

        if (meta instanceof SuspiciousStewMeta ssm) {
            for (PotionEffect pe : ssm.getCustomEffects()) {
                appendEffectTypeTokens(sb, pe.getType());
            }
        }

        PotionContents potionContents = item.getData(DataComponentTypes.POTION_CONTENTS);
        if (potionContents !=  null) {
            // Primary
            for (PotionEffect effect : potionContents.potion().getPotionEffects()) {
                appendEffectTypeTokens(sb, effect.getType());
            }
            // Secondary
            for (PotionEffect effect : potionContents.customEffects()) {
                appendEffectTypeTokens(sb, effect.getType());
            }
        }

        return sb.toString().trim();
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
            String roman = StringUtil.toRoman(level);
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

    // ----- CONTAINER SCAN -----
    public static boolean containsMatchingItem(ItemStack stack, String qNorm, int depth) {
        if (stack == null || stack.isEmpty() || depth < 0) {
            return false;
        }

        // Check bundles
        try {
            BundleContents bundleContents = stack.getData(DataComponentTypes.BUNDLE_CONTENTS);
            if (bundleContents != null) {
                for (ItemStack itemStack : bundleContents.contents()) {
                    if (itemStack.isEmpty()) continue;
                    String innerText = buildSearchText(itemStack);
                    if (StringUtil.fuzzyContains(innerText, qNorm)) return true;
                    if (containsMatchingItem(itemStack, qNorm, depth - 1)) return true;
                }
            }
        } catch (Throwable e) {

        }

        // Shulker contents
        if (stack.hasItemMeta() && stack.getItemMeta() instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox box) {
            for (ItemStack inner : box.getInventory().getContents()) {
                if (inner == null || inner.isEmpty()) continue;
                String innerText = buildSearchText(inner);
                if (StringUtil.fuzzyContains(innerText, qNorm)) return true;
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

    public static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("[^a-z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
