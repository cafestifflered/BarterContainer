package com.stifflered.bartercontainer.gui.common;

import com.github.stefvanschie.inventoryframework.adventuresupport.TextHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;

import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.stifflered.bartercontainer.util.Messages;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A simple chest GUI with built-in pagination support.

 * Features:
 *  - Uses a {@link PaginatedPane} to store multiple pages of GuiItems.
 *  - Provides navigation controls (next/previous page buttons).
 *  - Displays a header row filled with blank items (cosmetic divider).
 *  - Optionally includes a "Back" button if a parent inventory is supplied.
 *  - NEW: optional footer item placed at bottom-right (slot x=8,y=0 of the nav row).

 * Structure of the GUI:
 *  - Row 0 → Header (blank filler items).
 *  - Rows 1–4 → PaginatedPane for the actual items.
 *  - Row 5 → Navigation row (page controls, back, and optional footer).
 */
public class SimplePaginator extends ChestGui {

    // Pane holding navigation controls (prev/next/back/footer)
    private final StaticPane navigation;

    // Optional parent inventory (for back navigation)
    private final @Nullable Inventory parent;

    // Main pageable content area
    private final PaginatedPane paginatedPane;

    // Arrow items are built from messages.yml inside the constructor (avoid static init timing issues)
    private final ItemStack pageUpItem;
    private final ItemStack pageDownItem;

    // NEW: optional footer item shown in the bottom-right corner of the nav row
    private final @Nullable ItemStack footerRightItem;

    /**
     * Backward-compatible constructor (no footer).
     *
     * @param rows        number of rows in the GUI
     * @param textHolder  GUI title
     * @param guiItemList list of items to paginate
     * @param parent      optional parent inventory (adds back button if present)
     */
    public SimplePaginator(int rows, @NotNull TextHolder textHolder, List<GuiItem> guiItemList, @Nullable Inventory parent) {
        this(rows, textHolder, guiItemList, parent, null);
    }

    /**
     * New constructor with optional footer item (placed bottom-right).
     *
     * @param rows             number of rows in the GUI
     * @param textHolder       GUI title
     * @param guiItemList      list of items to paginate
     * @param parent           optional parent inventory (adds back button if present)
     * @param footerRightItem  optional item to render at (8,0) on the navigation row
     */
    public SimplePaginator(int rows, @NotNull TextHolder textHolder, List<GuiItem> guiItemList,
                           @Nullable Inventory parent, @Nullable ItemStack footerRightItem) {
        super(rows, textHolder);

        // Prevents players from moving items in/out of the GUI
        this.setOnTopClick((event) -> event.setCancelled(true));
        this.parent = parent;
        this.footerRightItem = footerRightItem;

        // Build arrow items with display names from messages.yml
        this.pageUpItem = ItemUtil.wrapEdit(new ItemStack(Material.TIPPED_ARROW), (meta) -> {
            Components.name(meta, Messages.mm("gui.paginator.next"));
            ((PotionMeta) meta).setColor(Color.GREEN);
            meta.addItemFlags(ItemFlag.values());
        });
        this.pageDownItem = ItemUtil.wrapEdit(new ItemStack(Material.TIPPED_ARROW), (meta) -> {
            Components.name(meta, Messages.mm("gui.paginator.prev"));
            ((PotionMeta) meta).setColor(Color.RED);
            meta.addItemFlags(ItemFlag.values());
        });

        // Initialize the main pageable area (9 wide × 4 tall, starting at row 1)
        this.paginatedPane = new PaginatedPane(0, 1, 9, 4);
        this.paginatedPane.populateWithGuiItems(guiItemList);
        this.addPane(this.paginatedPane);

        // Header row (row 0) filled with blanks to visually separate UI
        StaticPane header = new StaticPane(0, 0, 9, 1);
        header.fillWith(ItemUtil.BLANK);
        this.addPane(header);

        // Navigation row at the bottom (row 5)
        this.navigation = new StaticPane(0, 5, 9, 1);
        this.addPane(this.navigation);

        // Initial render of navigation
        update();
    }

    /**
     * Refreshes navigation controls and updates the GUI.
     * Ensures proper enabling/disabling of arrows depending on page index.
     */
    @Override
    public void update() {
        this.navigation.clear();
        navigation.fillWith(ItemUtil.BLANK); // Reset row to blanks
        PaginatedPane pages = this.paginatedPane;

        // Add "Previous Page" arrow if not on the first page
        if (pages.getPage() > 0) {
            navigation.addItem(new com.github.stefvanschie.inventoryframework.gui.GuiItem(pageDownItem, event -> {
                if (pages.getPage() > 0) {
                    pages.setPage(pages.getPage() - 1);
                    this.update(); // Refresh GUI after page change
                }
            }), 3, 0);
        }

        // Add "Next Page" arrow if not on the last page
        if (pages.getPage() < pages.getPages() - 1) {
            navigation.addItem(new com.github.stefvanschie.inventoryframework.gui.GuiItem(pageUpItem, event -> {
                if (pages.getPage() < pages.getPages() - 1) {
                    pages.setPage(pages.getPage() + 1);
                    this.update(); // Refresh GUI after page change
                }
            }), 5, 0);
        }

        // Add a back button if parent inventory exists — place it at the FAR LEFT to avoid colliding with footer
        if (parent != null) {
            navigation.addItem(ItemUtil.back(parent), 0, 0);
        }

        // Place footer item (e.g., the catalogue search compass) in the BOTTOM-RIGHT slot
        if (footerRightItem != null) {
            navigation.addItem(new com.github.stefvanschie.inventoryframework.gui.GuiItem(footerRightItem), 8, 0);
        }

        // Call superclass update to finalize changes
        super.update();
    }
}
