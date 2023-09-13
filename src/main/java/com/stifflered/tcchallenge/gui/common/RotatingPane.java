package com.stifflered.tcchallenge.gui.common;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.util.Gui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.stifflered.tcchallenge.util.Maths;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RotatingPane<T extends Enum<?> & RotatingPane.IterableValue> {

    private final Gui gui;
    private final StaticPane pane;
    private final int x;
    private final int z;

    private final List<T> values;
    private int index;

    public RotatingPane(Gui gui, StaticPane pane, int x, int z, T defaultValue, T[] values) {
        this.gui = gui;
        this.pane = pane;
        this.x = x;
        this.z = z;
        this.values = List.of(values);
        this.setValue(defaultValue);
    }

    public void next() {
        this.index = Maths.wrap(this.index + 1, 0, this.values.size() - 1);
        this.refresh();
        this.gui.update();
    }

    public void refresh() {
        this.pane.addItem(new GuiItem(this.getValue().getItem(), (event) -> {
            event.setCancelled(true);
            this.next();
        }), this.x, this.z);
    }

    public void setValue(T value) {
        this.index = this.values.indexOf(value);

        if (this.index == -1) {
            this.index = 0;
        }
        this.refresh();
    }

    public T getValue() {
        return this.values.get(this.index);
    }


    public interface IterableValue {

        @NotNull
        ItemStack getItem();

    }

}
