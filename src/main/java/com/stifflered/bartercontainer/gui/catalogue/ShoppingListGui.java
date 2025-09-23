package com.stifflered.bartercontainer.gui.catalogue;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.player.ShoppingListManager;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.stifflered.bartercontainer.util.Messages;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * ShoppingListGui extends the general Catalogue view to add shopping-list behavior.

 * Differences from CatalogueGui:
 *  - Left-click: attempts to "receive" items (decrement needed amount) from the player's list.
 *  - Right-click: adds the clicked item to the player's shopping list.
 *  - Shift modifies the amount by 10 instead of 1 for fast bulk operations.

 * UX strings (lore, messages) are sourced from messages.yml (gui.shopping.*).
 */
public class ShoppingListGui extends CatalogueGui {

    /** The player viewing/using this GUI. */
    private final Player player;

    public ShoppingListGui(Player player) {
        this.player = player;
    }

    /**
     * Overrides item formatting to:
     *  - Clone the base item and apply shopping-list-specific lore.
     *  - Attach a click handler with shopping-list semantics:
     *      * Right-click → add to list (amount = 1 or 10 with shift).
     *      * Left-click  → receive from list (decrement required amount by 1 or 10).

     * Messages (from messages.yml):
     *  - gui.shopping.add      → placeholders: <amount>, <item>
     *  - gui.shopping.remove   → placeholders: <amount>, <item>
     *  - gui.shopping.not_removed (no placeholders)
     */
    @Override
    protected GuiItem formatItem(ItemStack baseItem, List<BarterStore> items) {
        return new GuiItem(ItemUtil.wrapEdit(baseItem.clone(), (meta) -> {
            // Apply lore lines defined for the shopping list context (from messages.yml)
            Components.lore(meta, Messages.mmList("gui.shopping.lore"));
        }), clickEvent -> {
            Player clicker = (Player) clickEvent.getWhoClicked();

            // Shift-click boosts amount from 1 → 10 for both add and receive operations
            int amount = clickEvent.isShiftClick() ? 10 : 1;

            if (clickEvent.isRightClick()) {
                // RIGHT CLICK: Add item to the player's shopping list
                BarterContainer.INSTANCE.getShoppingListManager().addItem(clicker, baseItem, amount);
                player.sendMessage(
                        Messages.mm("gui.shopping.add",
                                "amount", String.valueOf(amount),
                                "item", baseItem.getType().key().asString()
                        )
                );
            } else {
                // LEFT CLICK: Receive (decrement) the item from the player's list
                ShoppingListManager.MutateState state =
                        BarterContainer.INSTANCE.getShoppingListManager().receive(clicker, baseItem, amount);

                // Feedback varies by mutation result:
                //  - MODIFIED or REMOVED → success message (with placeholders)
                //  - NOTHING            → cannot remove message
                switch (state) {
                    case MODIFIED, REMOVED -> player.sendMessage(
                            Messages.mm("gui.shopping.remove",
                                    "amount", String.valueOf(amount),
                                    "item", baseItem.getType().key().asString()
                            )
                    );
                    case NOTHING -> player.sendMessage(Messages.mm("gui.shopping.not_removed"));
                }
            }
        });
    }
}
