package com.stifflered.bartercontainer.item.impl;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.BarterContainerConfiguration;
import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.barter.BarterStoreKeyImpl;
import com.stifflered.bartercontainer.item.ItemInstance;
import com.stifflered.bartercontainer.store.BarterStoreImpl;
import com.stifflered.bartercontainer.util.Components;
import com.stifflered.bartercontainer.util.ItemUtil;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class ShopListerItem extends ItemInstance {

    private static final Component SHOP_INVALID_BLOCK = Components.prefixedError(Component.text("The block you clicked is not a valid container!"));
    private static final Component NO_PERMISSION = Components.prefixedError(Component.text("No permission!"));

    private static final Sound ACTIVATED_SOUND = Sound.sound(org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, Sound.Source.BLOCK, 1, 1);

    public ShopListerItem() {
        super("shop_lister", BarterContainer.INSTANCE.getConfiguration().getShopListerConfiguration());
    }

    @Override
    public void interact(PlayerInteractEvent event) {
        event.setUseItemInHand(Event.Result.DENY);

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) {
            player.sendMessage(SHOP_INVALID_BLOCK);
            return;
        }
        if (!player.hasPermission("barterchests.create")) {
            player.sendMessage(NO_PERMISSION);
            return;
        }

        if (block.getType() != Material.BARREL) {
            player.sendMessage(SHOP_INVALID_BLOCK);
            return;
        }

        BlockState state = block.getState(false);
        if (state instanceof TileState tileState) {
            if (tileState instanceof Container container && !container.getInventory().isEmpty()) {
                player.sendMessage(Components.prefixedError(Component.text("You can only convert empty containers!")));
                return;
            }
            if (BarterManager.INSTANCE.getBarter(tileState.getLocation()).isPresent()) {
                player.sendMessage(Components.prefixedError(Component.text("There is already a barter container at this location.")));
                return;
            }

            List<Location> locationList = new ArrayList<>();
            locationList.add(state.getLocation());

            BarterManager.INSTANCE.createNewStore(
                    new BarterStoreImpl(new BarterStoreKeyImpl(UUID.randomUUID()), player.getPlayerProfile(), new ArrayList<>(), new ArrayList<>(), new ItemStack(Material.DIAMOND), locationList),
                    tileState.getPersistentDataContainer(),
                    state.getChunk()
            );


            player.sendMessage(Components.prefixedSuccess(Component.text("Conversion Successful. Your shop is now open!")));
            player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_WORK_MASON, Sound.Source.BLOCK, 1, 1));
            ItemUtil.subtract(event.getPlayer(), event.getItem());
            event.setUseInteractedBlock(Event.Result.DENY);
        } else {
            player.sendMessage(SHOP_INVALID_BLOCK);
        }
    }


}
