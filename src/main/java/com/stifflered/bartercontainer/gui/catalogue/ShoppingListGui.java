package com.stifflered.bartercontainer.gui.catalogue;

import com.github.stefvanschie.inventoryframework.gui.*;
import com.stifflered.bartercontainer.*;
import com.stifflered.bartercontainer.player.*;
import com.stifflered.bartercontainer.store.*;
import com.stifflered.bartercontainer.util.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.minimessage.*;
import net.kyori.adventure.text.minimessage.tag.resolver.*;
import org.bukkit.*;
import org.bukkit.conversations.*;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.function.*;

public class ShoppingListGui extends CatalogueGui {

    private static final BarterContainerConfiguration config = BarterContainer.INSTANCE.getConfiguration();
    private final Player player;

    public ShoppingListGui(Player player) {
        this.player = player;
    }

    @Override
    protected GuiItem formatItem(ItemStack baseItem, List<BarterStore> items) {
        return new GuiItem(ItemUtil.wrapEdit(baseItem.clone(), (meta) -> {
            Components.lore(meta, config.getShoppingListLore());
        }), clickEvent -> {
            Player clicker = (Player) clickEvent.getWhoClicked();

            int amount = clickEvent.isShiftClick() ? 10 : 1;
            if (clickEvent.isRightClick()) {
                BarterContainer.INSTANCE.getShoppingListManager().addItem(clicker, baseItem, amount);
                player.sendMessage(
                        MiniMessage.miniMessage().deserialize(
                                config.getShoppingListAddItemMessage(),
                                Placeholder.parsed("amount", String.valueOf(amount)),
                                Placeholder.parsed("item", baseItem.getType().key().asString())
                        )
                );
            } else {
                ShoppingListManager.MutateState state = BarterContainer.INSTANCE.getShoppingListManager().receive(clicker, baseItem, amount);
                player.sendMessage(
                        switch (state) {
                            case MODIFIED, REMOVED -> MiniMessage.miniMessage().deserialize(
                                    config.getShoppingListRemoveItemMessage(),
                                    Placeholder.parsed("amount", String.valueOf(amount)),
                                    Placeholder.parsed("item", baseItem.getType().key().asString())
                            );
                            case NOTHING -> config.getShoppingListNotRemovedItemMessage();
                        }
                );
            }
        });
    }
}