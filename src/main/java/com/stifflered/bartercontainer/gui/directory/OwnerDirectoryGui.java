package com.stifflered.bartercontainer.gui.directory;

import com.github.stefvanschie.inventoryframework.adventuresupport.TextHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.BarterShopOwnerLogManager;
import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.stifflered.bartercontainer.util.Messages;
import com.stifflered.bartercontainer.util.TrackingManager;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.Vector;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * OwnerDirectoryGui

 * Shows a paginated list of unique shop owners (as player heads).
 * - Clicking a head starts a particle trail to the nearest barrel owned by that player.
 * - Lore on each head includes top sellers across all of that owner's barrels (by Material).

 * Network-quiet rules:
 * - Never call SkullMeta#setOwningPlayer.
 * - Only call SkullMeta#setPlayerProfile if that profile ALREADY has textures, or for normal Java UUIDs.
 * - For Floodgate/synthetic UUIDs or unknown names, DON'T set a profile at all (generic head) unless
 *   you can supply a local textures property.
 */
public class OwnerDirectoryGui {
    private static final int ROWS = 6;

    private final BarterContainer plugin;
    private final Player viewer;

    public OwnerDirectoryGui(@NotNull BarterContainer plugin, @NotNull Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    public void open() {
        plugin.getLogger().fine("Opening OwnerDirectoryGui for " + viewer.getName());

        Component title = Messages.mm("gui.directory.title");
        List<GuiItem> items = buildOwnerItems();

        TextHolder titleHolder =
                com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder.of(title);

        var gui = new com.stifflered.bartercontainer.gui.common.SimplePaginator(
                ROWS,
                titleHolder,
                items,
                null
        );
        gui.show(viewer);
    }

    private List<GuiItem> buildOwnerItems() {
        Map<UUID, List<BarterStore>> byOwner = BarterManager.INSTANCE.getAll()
                .stream()
                .filter(Objects::nonNull)
                .map(store -> new AbstractMap.SimpleEntry<>(ownerIdOrNull(store), store))
                .filter(e -> e.getKey() != null)
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));

        List<Map.Entry<UUID, List<BarterStore>>> owners = byOwner.entrySet()
                .stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .sorted(
                        Comparator
                                .comparing((Map.Entry<UUID, List<BarterStore>> e) -> rawName(e.getKey()) == null)
                                .thenComparing(e -> {
                                    String n = rawName(e.getKey());
                                    return (n == null) ? "" : n;
                                }, String.CASE_INSENSITIVE_ORDER)
                )
                .toList();

        List<GuiItem> items = new ArrayList<>(owners.size());

        for (Map.Entry<UUID, List<BarterStore>> entry : owners) {
            UUID ownerId = entry.getKey();
            List<BarterStore> stores = entry.getValue();

            String ownerName = displayName(ownerId);

            ItemStack head = buildOwnerHead(ownerId, ownerName, stores);

            GuiItem guiItem = new GuiItem(head, click -> {
                click.setCancelled(true);

                Location nearest = findNearestOwnedLocation(stores, viewer.getWorld(), viewer.getLocation().toVector());
                if (nearest == null) {
                    viewer.sendMessage(Messages.mm("gui.directory.no_barrels"));
                    return;
                }

                TrackingManager.instance().track(viewer, nearest);
                viewer.closeInventory();
            });

            items.add(guiItem);
        }

        if (items.isEmpty()) {
            items.add(new GuiItem(ItemUtil.wrapEdit(new ItemStack(Material.BARRIER), meta -> {
                Components.name(meta, Messages.mm("gui.directory.no_owners_name"));
                Components.lore(meta, Messages.mm("gui.directory.no_owners_lore"));
            })));
        }

        return items;
    }

    /**
     * Build a player head while preventing Mojang lookups.
     * Strategy:
     *  - For likely Bedrock/unknown-name: don't set any profile unless a local textures value exists.
     *  - For normal Java UUIDs: set an UNCOMPLETED profile; if a local textures value exists, attach it.
     */
    private @NotNull ItemStack buildOwnerHead(@NotNull UUID ownerId,
                                              @NotNull String ownerName,
                                              @NotNull List<BarterStore> stores) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        final boolean isProblemOwner = isLikelyBedrockUuid(ownerId) || isBlank(ownerName);

        // Read optional fallback texture from config at runtime
        String fallbackValue = getFallbackTextureValue();
        String fallbackSignature = getFallbackTextureSignature();
        boolean haveFallback = fallbackValue != null && !fallbackValue.isBlank();

        if (isProblemOwner) {
            if (haveFallback) {
                // Use a profile ONLY when we can attach textures immediately (no completion)
                PlayerProfile profile = Bukkit.createProfile(ownerId, null);
                applyFallbackTexture(profile, fallbackValue, blankToNull(fallbackSignature));
                meta.setPlayerProfile(profile);
            }
            // else: leave WITHOUT any profile → renders generic head, zero network calls
        } else {
            // Normal Java UUID: safe to set an uncompleted profile; attach textures if available
            PlayerProfile profile = Bukkit.createProfile(ownerId, ownerName);
            if (haveFallback) {
                applyFallbackTexture(profile, fallbackValue, blankToNull(fallbackSignature));
            }
            meta.setPlayerProfile(profile);
        }

        // Name
        Components.name(meta, Messages.mm("gui.directory.head_name", "owner", ownerName));

        // Lore: top 3 sellers (by Material name only)
        List<Component> lore = new ArrayList<>();
        List<ItemSummary> top3 = resolveTopSellers(stores);
        if (top3.isEmpty()) {
            lore.add(Messages.mm("gui.directory.head_lore_no_sales"));
        } else {
            lore.add(Messages.mm("gui.directory.head_lore_top_header"));
            for (ItemSummary s : top3) {
                lore.add(Messages.mm("gui.directory.head_lore_top_item", "item", prettyMaterial(s.material())));
            }
        }
        meta.lore(lore);

        skull.setItemMeta(meta);
        return skull;
    }

    /** Raw name from Bukkit (null if player never joined this server). Skips Bedrock-style UUIDs entirely. */
    private String rawName(@NotNull UUID id) {
        try {
            if (isLikelyBedrockUuid(id)) return null;
            return Bukkit.getOfflinePlayer(id).getName();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Display name: raw name if known, otherwise messages.yml “unknown_owner”. */
    private String displayName(@NotNull UUID id) {
        String n = rawName(id);
        if (n != null && !n.isBlank()) return n;
        return Messages.plain(Messages.mm("gui.directory.unknown_owner"));
    }

    private String prettyMaterial(@NotNull Material m) {
        String name = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private Location findNearestOwnedLocation(@NotNull List<BarterStore> stores,
                                              @NotNull World preferredWorld,
                                              @NotNull Vector viewerPos) {
        Location best = null;
        double bestDist = Double.MAX_VALUE;

        // Prefer same-world barrels first
        for (BarterStore s : stores) {
            for (Location loc : safeLocations(s)) {
                if (!Objects.equals(loc.getWorld(), preferredWorld)) continue;
                double d = loc.toVector().distanceSquared(viewerPos);
                if (d < bestDist) { best = loc; bestDist = d; }
            }
        }
        if (best != null) return best;

        // Fallback: any world
        for (BarterStore s : stores) {
            for (Location loc : safeLocations(s)) {
                double d = loc.toVector().distanceSquared(viewerPos);
                if (d < bestDist) { best = loc; bestDist = d; }
            }
        }
        return best;
    }

    private List<Location> safeLocations(BarterStore s) {
        try {
            List<Location> locs = s.getLocations();
            return (locs == null) ? List.of() : locs.stream().filter(Objects::nonNull).toList();
        } catch (Throwable t) {
            return List.of();
        }
    }

    /**
     * Aggregate "top 3" materials sold across the provided stores.
     * Reads persisted transaction logs (all-time) and sums amounts by Material.
     */
    private List<ItemSummary> resolveTopSellers(@NotNull List<BarterStore> ownersStores) {
        final int TOP_N = 3;

        Map<Material, Long> counts = new HashMap<>();

        for (BarterStore store : ownersStores) {
            try {
                var records = BarterShopOwnerLogManager.listAllEntries(store.getKey());
                for (var rec : records) {
                    Material mat = rec.itemType();
                    if (mat == null) mat = Material.AIR;
                    counts.merge(mat, (long) Math.max(0, rec.amount()), Long::sum);
                }
            } catch (Exception e) {
                BarterContainer.INSTANCE.getLogger().warning(
                        "Failed to read logs for store " + store.getKey() + ": " + e.getMessage()
                );
            }
        }

        return counts.entrySet()
                .stream()
                .filter(e -> e.getKey() != Material.AIR && e.getValue() > 0)
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(TOP_N)
                .map(e -> new ItemSummary(e.getKey(), e.getValue()))
                .toList();
    }

    private static UUID ownerIdOrNull(BarterStore store) {
        try {
            return store.getPlayerProfile().getId();
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    /** Floodgate-style UUIDs often start with 00000000-0000-0000-0009-… */
    private static boolean isLikelyBedrockUuid(UUID id) {
        String s = id.toString();
        return s.startsWith("00000000-0000-0000-0009-");
    }

    // --- Config helpers (runtime-evaluated) ---

    private String getFallbackTextureValue() {
        try {
            return plugin.getConfig().getString("directory.fallback_texture.value", "");
        } catch (Throwable t) {
            return "";
        }
    }

    private String getFallbackTextureSignature() {
        try {
            return plugin.getConfig().getString("directory.fallback_texture.signature", "");
        } catch (Throwable t) {
            return "";
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** Attach a local textures property (no HTTP). */
    private static void applyFallbackTexture(PlayerProfile profile, String base64Value, String signatureOrNull) {
        if (base64Value == null || base64Value.isBlank()) return;
        profile.getProperties().clear();
        profile.setProperty(new ProfileProperty("textures", base64Value, signatureOrNull));
    }

    private record ItemSummary(Material material, long count) {}
}
