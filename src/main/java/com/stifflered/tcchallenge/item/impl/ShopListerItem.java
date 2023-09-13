package com.stifflered.tcchallenge.item.impl;

import com.stifflered.tcchallenge.BarterChallenge;
import com.stifflered.tcchallenge.item.ItemInstance;
import com.stifflered.tcchallenge.tree.blocks.CloneBlock;
import com.stifflered.tcchallenge.util.Components;
import com.stifflered.tcchallenge.util.ItemUtil;
import com.stifflered.tcchallenge.util.Messages;
import com.stifflered.tcchallenge.util.TreeUtil;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class ShopListerItem extends ItemInstance {

    private static final Component SHOP_INVALID_BLOCK = Components.prefixedError(Component.text("The block you assigned is not a valid container!"));
    private static final Sound ACTIVATED_SOUND = Sound.sound(org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, Sound.Source.BLOCK, 1, 1);

    public ShopListerItem() {
        super("shop_lister",
                ItemUtil.wrapEdit(new ItemStack(Material.NAME_TAG), (meta) -> {
                    Components.name(meta, Component.text("Shop Lister"));
                    Components.lore(meta, Components.miniSplit("""
                            <white>Click me on a container to
                            activate your bartering container!
                            """));
                })
        );
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


        BlockState state = block.getState(false);
        if (state instanceof TileState tileState) {

            ItemUtil.subtract(event.getPlayer(), event.getItem());
        } else {
            player.sendMessage(SHOP_INVALID_BLOCK);
        }
    }


}
