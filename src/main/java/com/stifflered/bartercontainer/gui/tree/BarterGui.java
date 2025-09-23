package com.stifflered.bartercontainer.gui.tree;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.Pane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;

import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.gui.tree.buttons.SetPriceGuiItem;
import com.stifflered.bartercontainer.gui.tree.buttons.ViewContentsGuiItem;
import com.stifflered.bartercontainer.gui.tree.buttons.ViewCurrencyGuiItem;
import com.stifflered.bartercontainer.gui.tree.buttons.ViewLogsGuiItem;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.stifflered.bartercontainer.util.Messages;
import com.stifflered.bartercontainer.util.Sounds;

import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

/**
 * Owner/manager-facing GUI for a single {@link BarterStore}.

 * Visual polish:
 *  - Alternating glass-pane background (tooltip hidden) under the controls.
 *  - Open-chest sound when shown.

 * Behavior:
 *  - All top-inventory clicks cancelled; buttons handle their own clicks.
 *  - Store saved on close.
 *  - Title comes from messages.yml: gui.main.title (<player> placeholder).
 */
public class BarterGui extends ChestGui {

    public BarterGui(BarterStore barterStore) {
        super(
                3,
                ComponentHolder.of(
                        Messages.mm(
                                "gui.main.title",
                                "player",
                                java.util.Objects.toString(barterStore.getPlayerProfile().getName(), "Shop")
                        )
                )
        );

        // Global interaction policy: top clicks are read-only
        this.setOnGlobalClick(event -> event.setCancelled(true));

        /* ---------- Background (LOWEST priority so it never covers buttons) ---------- */
        StaticPane bg = new StaticPane(0, 0, 9, 3);
        bg.setPriority(Pane.Priority.LOWEST);

        ItemStack dark  = ItemUtil.BLANK; // black glass (tooltip hidden)
        ItemStack light = blankPane();    // gray glass (tooltip hidden)

        // Subtle checkerboard
        for (int y = 0; y < bg.getHeight(); y++) {
            for (int x = 0; x < bg.getLength(); x++) {
                boolean useLight = ((x + y) & 1) == 0;
                bg.addItem(new com.github.stefvanschie.inventoryframework.gui.GuiItem(
                        useLight ? light : dark, e -> e.setCancelled(true)
                ), x, y);
            }
        }
        this.getInventoryComponent().addPane(bg);

        /* ---------- Foreground controls (HIGHEST priority) ---------- */
        StaticPane pane = ItemUtil.wrapGui(this.getInventoryComponent());
        pane.setPriority(Pane.Priority.HIGHEST);

        // (2,1) View shop contents
        pane.addItem(new ViewContentsGuiItem(barterStore), 2, 1);

        // (4,1) Set price
        pane.addItem(new SetPriceGuiItem(barterStore), 4, 1);

        // (6,1) View currency/bank
        pane.addItem(new ViewCurrencyGuiItem(barterStore), 6, 1);

        // (8,0) View logs
        pane.addItem(new ViewLogsGuiItem(barterStore), 8, 0);

        // Persist when closed
        this.setOnClose(event -> BarterManager.INSTANCE.save(barterStore));
    }

    /** Play an open sound when this GUI is shown. */
    @Override
    public void show(@NotNull HumanEntity human) {
        super.show(human);
        if (human instanceof Player p) {
            Sounds.openChest(p);
        }
    }

    /** Tooltip-hidden filler pane (gray glass). */
    private static ItemStack blankPane() {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        stack.editMeta((ItemMeta meta) -> meta.setHideTooltip(true));
        return stack;
    }
}
