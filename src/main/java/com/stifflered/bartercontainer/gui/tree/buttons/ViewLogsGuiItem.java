package com.stifflered.bartercontainer.gui.tree.buttons;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.gui.tree.LogBookHubGui;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.stifflered.bartercontainer.util.Messages;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;

/**
 * GUI button that opens the new 9-slot "Logs & Analytics" hub.

 * Previous behavior:
 *  - Opened a loading book and asynchronously built paginated transaction logs.

 * New behavior (temporary / inert):
 *  - Immediately opens {@link LogBookHubGui} (icons do nothing yet).
 *  - No async work, no book UI. Click-cancelling is handled centrally by BarterInventoryListener.
 */
public class ViewLogsGuiItem extends GuiItem {

    /**
     * Constructs a GUI button for viewing logs of the given store.
     * The store is accepted for API compatibility; not used yet.
     *
     * @param store The barter store whose logs/analytics are to be explored.
     */
    public ViewLogsGuiItem(BarterStore store) {
        super(
                // Display item defined in configuration (view-logs-item),
                // but name/lore are from messages.yml to keep text centralized.
                ItemUtil.wrapEdit(
                        BarterContainer.INSTANCE.getConfiguration().getViewLogsItem(),
                        meta -> {
                            Components.name(meta, Messages.mm("gui.tree.view_logs.name"));
                            Components.lore(meta, Messages.mmList("gui.tree.view_logs.lore"));
                        }
                ),
                event -> {
                    HumanEntity clicker = event.getWhoClicked();
                    if (clicker instanceof Player player) {
                        LogBookHubGui.open(player, store);
                    }
                    // No further action; central listener will handle cancelling interactions.
                }
        );
    }
}
