package com.stifflered.bartercontainer.gui.common;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.util.Gui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.stifflered.bartercontainer.util.Maths;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A tiny helper that renders a single slot in a GUI and cycles through a finite set of values
 * (backed by an enum). Clicking the slot advances to the next value, wrapping at the end.

 * Intended usage:
 *  - Define an enum that implements {@link RotatingPane.IterableValue} and returns an ItemStack
 *    for each enum constant (the visual shown in the slot).
 *  - Construct a RotatingPane with that enum type and a default value.
 *  - Place it at (x,z) in a {@link StaticPane}, tied to a parent {@link Gui}.
 *  - Each click swaps the enum value and re-renders the slot.

 * Notes:
 *  - {@link Maths#wrap(int, int, int)} is used for circular index arithmetic.
 *  - {@link Gui#update()} is called after advancing to trigger a visual refresh.
 *  - The slot is self-contained: it installs its own click handler to rotate.

 * Type parameter:
 *  - T must be an enum type AND implement IterableValue so we can:
 *      * compute index from values list
 *      * fetch an ItemStack representation for the current value
 */
public class RotatingPane<T extends Enum<?> & RotatingPane.IterableValue> {

    /** The parent InventoryFramework GUI to update after rotations. */
    private final Gui gui;
    /** The pane to which we add the clickable slot. */
    private final StaticPane pane;
    /** X coordinate within the pane (column). */
    private final int x;
    /** Z coordinate within the pane (row). */
    private final int z;

    /** Ordered list of enum values that we rotate through. */
    private final List<T> values;
    /** Current index in {@link #values}. */
    private int index;

    /**
     * Create a rotating slot anchored at (x,z) in the given pane.
     *
     * @param gui          parent GUI to be updated on changes
     * @param pane         UI pane where the slot is placed
     * @param x            column within the pane
     * @param z            row within the pane
     * @param defaultValue initial enum value to display
     * @param values       full set of enum constants (usually T.values())
     */
    public RotatingPane(Gui gui, StaticPane pane, int x, int z, T defaultValue, T[] values) {
        this.gui = gui;
        this.pane = pane;
        this.x = x;
        this.z = z;
        this.values = List.of(values);
        this.setValue(defaultValue);
    }

    /**
     * Advance to the next enum value, wrap at end, re-render, and update the GUI.
     */
    public void next() {
        this.index = Maths.wrap(this.index + 1, 0, this.values.size() - 1);
        this.refresh();
        this.gui.update();
    }

    /**
     * (Re)place the clickable item at (x,z) reflecting the current enum value.
     * Clicking the slot cancels the event and calls {@link #next()}.
     */
    public void refresh() {
        this.pane.addItem(new GuiItem(this.getValue().getItem(), (event) -> {
            event.setCancelled(true);
            this.next();
        }), this.x, this.z);
    }

    /**
     * Set the current enum value (by index in {@link #values}). If not found,
     * defaults to index 0. Then refresh the displayed slot.
     */
    public void setValue(T value) {
        this.index = this.values.indexOf(value);

        if (this.index == -1) {
            this.index = 0;
        }
        this.refresh();
    }

    /** @return the currently selected enum value. */
    public T getValue() {
        return this.values.get(this.index);
    }

    /**
     * Contract for enum constants used by RotatingPane:
     *  - Must be able to supply an ItemStack that visually represents the option.
     */
    public interface IterableValue {

        @NotNull
        ItemStack getItem();

    }

}