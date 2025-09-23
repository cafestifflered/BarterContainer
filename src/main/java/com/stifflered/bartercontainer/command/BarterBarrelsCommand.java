package com.stifflered.bartercontainer.command;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.item.ItemInstances;
import com.stifflered.bartercontainer.util.Messages;
import com.stifflered.bartercontainer.util.TimeUtil;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Root admin command entrypoint registered via plugin.yml:

 *   /barterbarrels <reload|givelister|givefixer>

 * Subcommands:
 *  - reload                          → hot-reloads config.yml, messages.yml, and TimeUtil (UTC formatter)
 *  - givelister <player> <amount?>   → give Shop Lister item (default amount = 1)
 *  - givefixer  <player> <amount?>   → give Fixer Stick item (default amount = 1)

 * Permission model (matches plugin.yml):
 *  - barterbarrels.admin : base node, default op (optional—kept for safety)
 *      - barterbarrels.reload     (default op)
 *      - barterbarrels.givelister (default op)
 *      - barterbarrels.givefixer  (default op)

 * Implementation notes:
 *  - The class implements TabExecutor to provide tab completion.
 *  - Tab completion is permission-aware via the can(...) helper:
 *      a sender sees suggestions only for subcommands they can execute.
 *  - We always clone the singleton items before setting the amount to avoid mutating the cached instances.
 *  - All player-facing text goes through Messages.mm(...) where keys exist.
 */
public final class BarterBarrelsCommand implements TabExecutor {

    /**
     * Tiny helper: allow either the specific node OR the parent admin node.
     * This keeps tab-complete and runtime checks consistent and admin-friendly.
     */
    private static boolean can(CommandSender sender, String node) {
        return sender.hasPermission(node) || sender.hasPermission("barterbarrels.admin");
    }

    // ---------------------------------------------------------------------
    // Command execution
    // ---------------------------------------------------------------------
    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        // No subcommand → quick usage hint (kept as a plain component for simplicity).
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /" + label + " <reload|givelister|givefixer>"));
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            // --------------------------------------------
            // /barterbarrels reload
            // --------------------------------------------
            case "reload" -> {
                if (!can(sender, "barterbarrels.reload")) {
                    sender.sendMessage(Messages.mm("commands.common.no_permission"));
                    return true;
                }

                sender.sendMessage(Messages.mm("commands.reload.start"));
                long start = System.nanoTime();
                int steps = 0;

                try {
                    // (1) Reload config.yml so new values are in memory.
                    BarterContainer.INSTANCE.reloadConfig();
                    FileConfiguration cfg = BarterContainer.INSTANCE.getConfig();
                    steps++;

                    // (2) Reload messages.yml (MiniMessage text).
                    Messages.reload();
                    steps++;

                    // (3) Re-initialize time helpers dependent on config.
                    TimeUtil.reloadFromConfig(cfg);
                    steps++;

                    long tookMs = (System.nanoTime() - start) / 1_000_000L;
                    Component ok = Messages.mm("commands.reload.success",
                            "ms", String.valueOf(tookMs),
                            "count", String.valueOf(steps));
                    sender.sendMessage(ok);
                } catch (Throwable t) {
                    sender.sendMessage(Messages.mm("commands.reload.fail",
                            "detail", String.valueOf(t.getMessage())));
                    BarterContainer.INSTANCE.getLogger().severe("Reload failed: " + t.getMessage());
                }
                return true;
            }

            // --------------------------------------------
            // /barterbarrels givelister <player> <amount?>
            // --------------------------------------------
            case "givelister" -> {
                if (!can(sender, "barterbarrels.givelister")) {
                    sender.sendMessage(Messages.mm("commands.common.no_permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /" + label + " givelister <player> <amount?>"));
                    return true;
                }

                String targetName = args[1];
                int amount = (args.length >= 3) ? parsePositive(args[2]) : 1;

                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found: " + targetName));
                    return true;
                }

                // Clone the singleton so we don't mutate the shared instance.
                var base = ItemInstances.SHOP_LISTER_ITEM.getItem();
                var stack = base.clone();
                stack.setAmount(Math.max(1, amount));

                // ✅ Apply messages.yml display NOW (so it looks right in inventory)
                {
                    var meta = stack.getItemMeta();
                    if (meta != null) {
                        meta.displayName(Messages.mm("items.shop_lister.item_name"));
                        var lore = Messages.mmList("items.shop_lister.item_lore");
                        if (!lore.isEmpty()) meta.lore(lore);
                        stack.setItemMeta(meta);
                    }
                }

                target.getInventory().addItem(stack);
                return true;
            }

            // --------------------------------------------
            // /barterbarrels givefixer <player> <amount?>
            // --------------------------------------------
            case "givefixer" -> {
                if (!can(sender, "barterbarrels.givefixer")) {
                    sender.sendMessage(Messages.mm("commands.common.no_permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /" + label + " givefixer <player> <amount?>"));
                    return true;
                }

                String targetName = args[1];
                int amount = (args.length >= 3) ? parsePositive(args[2]) : 1;

                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found: " + targetName));
                    return true;
                }

                // Clone the singleton so we don't mutate the shared instance.
                var base = ItemInstances.SHOP_FIXER_ITEM.getItem();
                var stack = base.clone();
                stack.setAmount(Math.max(1, amount));

                // ✅ Apply messages.yml display NOW (name only; no lore defined for fixer)
                {
                    var meta = stack.getItemMeta();
                    if (meta != null) {
                        meta.displayName(Messages.mm("items.shop_fixer.item_name"));
                        stack.setItemMeta(meta);
                    }
                }

                target.getInventory().addItem(stack);
                return true;
            }

            // --------------------------------------------
            // Unknown subcommand
            // --------------------------------------------
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand: " + sub));
                return true;
            }
        }
    }

    // ---------------------------------------------------------------------
    // Tab completion (permission-aware)
    // ---------------------------------------------------------------------

    // Keeping this just for static reference; we’ll still gate suggestions by perms below.
    private static final List<String> SUBS = List.of("reload", "givelister", "givefixer");

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender,
                                               @NotNull Command command,
                                               @NotNull String alias,
                                               @NotNull String[] args) {
        // 1st arg → subcommand; suggest only what the sender can actually run.
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            List<String> out = new ArrayList<>();
            if (can(sender, "barterbarrels.reload")     && "reload".startsWith(p))     out.add("reload");
            if (can(sender, "barterbarrels.givelister") && "givelister".startsWith(p)) out.add("givelister");
            if (can(sender, "barterbarrels.givefixer")  && "givefixer".startsWith(p))  out.add("givefixer");
            return out;
        }

        // 2nd arg → player names (only for give* subs and only if they have permission)
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            boolean wantsLister = sub.equals("givelister") && can(sender, "barterbarrels.givelister");
            boolean wantsFixer  = sub.equals("givefixer")  && can(sender, "barterbarrels.givefixer");
            if (wantsLister || wantsFixer) {
                String p = args[1].toLowerCase();
                List<String> out = new ArrayList<>();
                Bukkit.getOnlinePlayers().forEach(pl -> {
                    String n = pl.getName();
                    if (n.toLowerCase().startsWith(p)) out.add(n);
                });
                return out;
            }
            return List.of();
        }

        // 3rd arg → amount suggestions (only for give* subs and only if they have permission)
        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if ((sub.equals("givelister") && can(sender, "barterbarrels.givelister")) ||
                    (sub.equals("givefixer")  && can(sender, "barterbarrels.givefixer"))) {
                return List.of("1", "16", "64");
            }
            return List.of();
        }

        return List.of();
    }

    // ---------------------------------------------------------------------
    // Utilities
    // ---------------------------------------------------------------------

    /**
     * Parse a positive integer; returns 1 on invalid/negative input.
     */
    private static int parsePositive(String s) {
        try {
            int v = Integer.parseInt(s);
            return v > 0 ? v : 1;
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }
}
