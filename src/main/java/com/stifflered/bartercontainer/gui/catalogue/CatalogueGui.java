package com.stifflered.bartercontainer.gui.catalogue;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.gui.common.SimplePaginator;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.BedrockUtil;
import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemSearchTokenizer;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.stifflered.bartercontainer.util.Messages;
import com.stifflered.bartercontainer.util.StringUtil;
import com.stifflered.bartercontainer.util.TrackingManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.response.CustomFormResponse;
import org.geysermc.cumulus.response.result.ValidFormResponseResult;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Catalogue GUI:
 * - Aggregates sale items from all BarterStores on the server.
 * - Displays a paginated chest GUI of unique items being sold.
 * - Clicking an item begins tracking to the nearest shop location that sells it.
 * <p>
 * Search:
 * - Fuzzy matching against material + custom name + potion effects (incl. 1.21+ Potion Contents) + enchantments.
 * - Also searches inside shulker boxes and bundles (via reflection), recursively.
 * <p>
 * UX:
 * - Bottom-right search button opens a one-shot text prompt (Java chat / Bedrock form→sign→anvil fallback).
 * - All clicks are cancelled except our handlers.
 */
public class CatalogueGui extends SimplePaginator {

    private static NamespacedKey searchMarkerKey() {
        return new NamespacedKey(BarterContainer.INSTANCE, "catalog_search_button");
    }

    public static CompletableFuture<CatalogueGui> loadAndOpen(String filterQuery, Player player) {
        return loadAndOpen(filterQuery, player, CatalogueGui::new, CatalogueGui::formatItem);
    }

    public static <T extends CatalogueGui> CompletableFuture<T> loadAndOpen(String filterQuery, Player player, BiFunction<List<GuiItem>, String, T> callback, BiFunction<ItemStack, List<BarterStore>, GuiItem> prettyfier) {
        return CompletableFuture.supplyAsync(() -> {
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
                items.add(prettyfier.apply(entry.getKey().clone(), entry.getValue()));
            }

            items.sort(Comparator.comparing(o -> o.getItem().getType().key().toString()));
            return callback.apply(items, filterQuery);
        }).thenApplyAsync(catalogueGui -> {
            if (filterQuery != null && catalogueGui.pages.isEmpty()) {
                player.sendMessage(Messages.mm("gui.catalogue.search.no_results", "query", filterQuery));
            }

            catalogueGui.show(player);
            return catalogueGui;
        }, Bukkit.getScheduler().getMainThreadExecutor(BarterContainer.INSTANCE));
    }


    private final String query;

    protected CatalogueGui(List<GuiItem> items, String query) {
        super(6, ComponentHolder.of(Messages.mm("gui.catalogue.title")), items, null);
        this.query = query;
        this.setOnGlobalClick(event -> event.setCancelled(true));
        SearchChat.ensureInstalled();

        StaticPane pane = ItemUtil.wrapGui(this.getInventoryComponent());
        pane.addItem(new GuiItem(buildSearchButton(), inventoryClickEvent -> {
            Player player = (Player) inventoryClickEvent.getWhoClicked();
            if (BedrockUtil.isBedrock(player)) {
                openBedrockDialogue(player, (queryRes) -> {
                    if (query == null || query.isBlank()) {
                        CatalogueGui.loadAndOpen(null, player);
                    } else {
                        player.sendMessage(Messages.mm("gui.catalogue.search.working", "query", queryRes));
                        CatalogueGui.loadAndOpen(queryRes, player);
                    }
                });
                return;
            }

            player.sendMessage(Messages.mm("gui.catalogue.search.prompt"));
            SearchChat.register(player, (queryRes) -> {
                if (query == null) {
                    CatalogueGui.loadAndOpen(null, player);
                } else {
                    player.sendMessage(Messages.mm("gui.catalogue.search.working", "query", queryRes));
                    CatalogueGui.loadAndOpen(queryRes, player);
                }
            });
            player.closeInventory();

        }), 8, 0);
    }


    private static GuiItem formatItem(ItemStack baseItem, List<BarterStore> items) {
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

    private static boolean matchesQuery(ItemStack stack, String rawQuery) {
        String q = ItemSearchTokenizer.normalize(rawQuery);
        if (q.isEmpty()) return true;

        String target = ItemSearchTokenizer.buildSearchText(stack);
        if (StringUtil.fuzzyContains(target, q)) return true;

        return ItemSearchTokenizer.containsMatchingItem(stack, q, 2);
    }

    // ========================= INPUT FLOWS =========================

    private static final class SearchChat implements Listener {
        private static final Map<UUID, java.util.function.Consumer<String>> PENDING = new ConcurrentHashMap<>();
        private static volatile boolean installed = false;

        private SearchChat() {
        }

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
            try {
                e.viewers().clear();
            } catch (Throwable ignored) {
            }
            try {
                e.renderer((source, displayName, message, viewer) -> Component.empty());
            } catch (Throwable ignored) {
            }

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

    public static void openBedrockDialogue(Player bukkitPlayer, java.util.function.Consumer<String> callback) {
        String title = Messages.fmt("gui.catalogue.search.bedrock_title");
        String label = Messages.fmt("gui.catalogue.search.bedrock_label");
        String placeholder = Messages.fmt("gui.catalogue.search.bedrock_placeholder");


        try {
            CustomForm form = CustomForm.builder()
                    .title(title)
                    .input(label, placeholder, "")
                    .input(label, placeholder)
                    .input(label)
                    .input(placeholder, "")
                    .resultHandler((customForm, customFormResponseFormResponseResult) -> {
                        boolean closed = customFormResponseFormResponseResult.isClosed();
                        if (closed) {
                            Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> callback.accept(null));
                            return;
                        }

                        if (customFormResponseFormResponseResult instanceof ValidFormResponseResult<CustomFormResponse> result) {
                            String text = result.response().asInput(0);
                            final String val = (text == null) ? "" : text.trim();
                            Bukkit.getScheduler().runTask(BarterContainer.INSTANCE, () -> callback.accept(val));
                        }

                    }).build();


            Bukkit.getScheduler().runTaskLater(BarterContainer.INSTANCE, () -> {
                FloodgateApi.getInstance().getPlayer(bukkitPlayer.getUniqueId()).sendForm(form);
            }, 2L);

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}
