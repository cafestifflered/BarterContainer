package com.stifflered.bartercontainer.item.impl;

import com.stifflered.bartercontainer.*;
import com.stifflered.bartercontainer.barter.*;
import com.stifflered.bartercontainer.item.*;
import com.stifflered.bartercontainer.store.*;
import com.stifflered.bartercontainer.util.*;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;

import java.util.*;

public class ShopFixerItem extends ItemInstance {

    private static final Component SHOP_INVALID_BLOCK = Components.prefixedError(Component.text("The block you clicked is not a valid container!"));
    private static final Component NO_PERMISSION = Components.prefixedError(Component.text("No permission!"));

    private static final Sound ACTIVATED_SOUND = Sound.sound(org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, Sound.Source.BLOCK, 1, 1);

    public ShopFixerItem() {
        super("shop_fixer", ItemUtil.wrapEdit(new ItemStack(Material.STICK), (meta) -> {
            Components.name(meta, Component.text("FIXER STICK"));
        }));
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
        if (!player.hasPermission("barterchests.admin")) {
            player.sendMessage(NO_PERMISSION);
            return;
        }

        Optional<BarterStore> storeOptional = BarterManager.INSTANCE.getBarter(block.getLocation());
        if (storeOptional.isPresent()) {
            BarterStore store = storeOptional.get();
            if (store.getLocations().isEmpty()) {
                player.sendMessage(Components.prefixedSuccess(Component.text("Fixed location!")));
                player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_WORK_MASON, Sound.Source.BLOCK, 1, 1));
                store.getLocations().add(block.getLocation());
            }

            event.setUseInteractedBlock(Event.Result.DENY);

            BarterManager.INSTANCE.save(store);
        } else {
            player.sendMessage(SHOP_INVALID_BLOCK);
        }
    }


}
