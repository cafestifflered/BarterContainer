package com.stifflered.bartercontainer.command;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.gui.catalogue.ShoppingListGui;
import com.stifflered.bartercontainer.player.ShoppingListManager;
import com.stifflered.bartercontainer.util.ListPaginator;
import com.stifflered.bartercontainer.util.Messages;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Command: /shoppinglist

 * Purpose:
 *  - Displays and manages a player’s shopping list of desired items.
 *  - Renders the list as an in-game Adventure book UI with clickable actions.

 * Permissions:
 *  - Requires "barterchests.shopping-list".

 * Behavior:
 *  - Builds a list of entries from ShoppingListManager, plus a special “+” entry for adding new items.
 *  - Paginates entries (13 per page).
 *  - For each entry:
 *      * Regular entry → shows styled component + ❌ to remove item.
 *      * Special “+” entry → shows ➕ to open ShoppingListGui to add items.
 *  - Opens a book for the player containing all pages of components.

 * Notes:
 *  - Registered via plugin.yml.
 *  - Uses Adventure’s Book API to present interactive pages (instead of GUIs).
 *  - ClickEvent.callback is used for dynamic in-session interactions.
 */
public class ShoppingListCommand implements CommandExecutor {

    /** Singleton shopping list manager from plugin instance. */
    private final ShoppingListManager manager = BarterContainer.INSTANCE.getShoppingListManager();

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        // Guard: players only
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.mm("commands.common.players_only"));
            return true;
        }

        if (!sender.hasPermission("barterchests.shopping-list")) {
            sender.sendMessage(Messages.mm("commands.common.no_permission"));
            return true;
        }

        // Special "add new item" entry used as a placeholder in the list.
        ShoppingListManager.ShoppingList.ShoppingListEntry specialAddEntry =
                new ShoppingListManager.ShoppingList.ShoppingListEntry(null, null);

        // Collect current entries + add the special “+” entry at the end.
        List<ShoppingListManager.ShoppingList.ShoppingListEntry> entries =
                new ArrayList<>(manager.getShoppingList(player).toDisplayList());
        entries.add(specialAddEntry);

        // Paginate entries (13 per page).
        ListPaginator<ShoppingListManager.ShoppingList.ShoppingListEntry> entryListPaginator =
                new ListPaginator<>(entries, 13);

        // Build Adventure pages.
        List<Component> pages = new ArrayList<>();
        for (int i = 0; i < entryListPaginator.getTotalPages(); i++) {
            TextComponent.Builder builder = Component.text();
            for (ShoppingListManager.ShoppingList.ShoppingListEntry record : entryListPaginator.getPage(i)) {
                if (record != specialAddEntry) {
                    // Regular entry: show styled component + ❌ clickable to remove item.
                    builder.append(record.styled()
                            .append(Component.space()
                                    .append(BarterContainer.INSTANCE.getConfiguration().getShoppingListX())
                                    .clickEvent(ClickEvent.callback(audience -> {
                                        audience.sendMessage(
                                                MiniMessage.miniMessage().deserialize(
                                                        BarterContainer.INSTANCE.getConfiguration().getShoppingListRemoveItemMessage(),
                                                        Placeholder.parsed("amount", "all"),
                                                        Placeholder.parsed("item", record.itemStack().getType().key().asString())
                                                )
                                        );
                                        manager.removeItem((Player) audience, record.itemStack());
                                    })))).appendNewline();
                } else {
                    // Special "add new item" entry: ➕ button opens ShoppingListGui.
                    builder.append(BarterContainer.INSTANCE.getConfiguration().getShoppingListPlus())
                            .clickEvent(ClickEvent.callback(audience -> {
                                if (audience instanceof Player player1) {
                                    player1.closeInventory();
                                    ShoppingListGui.open(player1);
                                }
                            }))
                            .appendNewline();
                }
            }
            pages.add(builder.build());
        }

        // Open the interactive book UI with built pages.
        player.openBook(Book.book(Component.text("Data"), Component.text("?"), pages));
        return true;
    }
}
