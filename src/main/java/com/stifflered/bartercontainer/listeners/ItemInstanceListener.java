package com.stifflered.bartercontainer.listeners;

import com.stifflered.bartercontainer.item.ItemInstance;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener that routes player events to the appropriate {@link ItemInstance}.

 * Purpose:
 *  - Intercepts interaction (click) and placement events for players.
 *  - If the involved ItemStack is tagged as an ItemInstance, delegate to that instance’s handler.

 * Event flow:
 *  - PlayerInteractEvent (priority HIGH): calls instance.interact(event).
 *  - BlockPlaceEvent (priority HIGH): calls instance.place(event).

 * Design:
 *  - Uses ItemInstance.fromStack(...) to resolve which (if any) logical item is being used.
 *  - Delegates logic entirely to the ItemInstance subclass, keeping this listener generic.

 * Notes:
 *  - Priority HIGH ensures these handlers run after default mechanics but before MONITOR.
 *  - Final methods: subclassing of this listener isn’t intended.
 */
public class ItemInstanceListener implements Listener {

    /** Route PlayerInteractEvent to the corresponding ItemInstance (if present). */
    @EventHandler(priority = EventPriority.HIGH)
    public final void onInteract(PlayerInteractEvent event) {
        ItemInstance instance = this.get(event.getItem());
        if (instance != null) {
            instance.interact(event);
        }
    }

    /** Route BlockPlaceEvent to the corresponding ItemInstance (if present). */
    @EventHandler(priority = EventPriority.HIGH)
    public final void onPlace(BlockPlaceEvent event) {
        ItemInstance instance = this.get(event.getItemInHand());
        if (instance != null) {
            instance.place(event);
        }
    }

    /** Helper: resolve ItemInstance from an ItemStack (returns null if not tagged). */
    private ItemInstance get(ItemStack stack) {
        return ItemInstance.fromStack(stack);
    }
}
