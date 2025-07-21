package com.stifflered.bartercontainer.listeners;

import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.barter.permission.BarterPermission;
import com.stifflered.bartercontainer.barter.permission.BarterRole;
import com.stifflered.bartercontainer.barter.serializers.LegacyBarterSerializer;
import com.stifflered.bartercontainer.event.RemoveBarterContainer;
import com.stifflered.bartercontainer.gui.tree.BarterBuyGui;
import com.stifflered.bartercontainer.gui.tree.BarterGui;
import com.stifflered.bartercontainer.item.ItemInstance;
import com.stifflered.bartercontainer.item.ItemInstances;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.util.ItemUtil;
import com.stifflered.bartercontainer.util.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class BarterBlockListener implements Listener {

    private static final LegacyBarterSerializer legacy = new LegacyBarterSerializer();

    @EventHandler
    public void protect(BlockBreakEvent event) {
        Location location = event.getBlock().getLocation();
        BarterManager.INSTANCE.getBarter(location)
                .ifPresent((container) -> {
                    event.setCancelled(!container.canBreak(event.getPlayer()));
                    if (!event.isCancelled()) {
                        BarterManager.INSTANCE.removeBarterContainer(location);
                        ItemUtil.giveItemOrThrow(event.getPlayer(), ItemInstances.SHOP_LISTER_ITEM.getItem());
                    }
                });
    }

    @EventHandler
    public void drop(BlockExplodeEvent event) {
        event.blockList().removeIf((block) -> BarterManager.INSTANCE.getBarter(block.getLocation()).isPresent());
    }

    @EventHandler
    public void drop(EntityExplodeEvent event) {
        event.blockList().removeIf((block) -> BarterManager.INSTANCE.getBarter(block.getLocation()).isPresent());
    }

    @EventHandler
    public void change(InventoryMoveItemEvent event) {
        if (event.getDestination().getHolder(false) instanceof Hopper hopper) {
            BarterManager.INSTANCE.getBarter(hopper.getLocation())
                    .ifPresent((container) -> {
                        event.setCancelled(true);
                    });
        }

        if (event.getSource().getHolder(false) instanceof Hopper hopper) {
            BarterManager.INSTANCE.getBarter(hopper.getLocation())
                    .ifPresent((container) -> {
                        event.setCancelled(true);
                    });
        }
    }


//    @EventHandler
//    public void fade(BlockFadeEvent event) {
//        ImportantBlock importantBlock = this.fromLocation(event.getBlock().getLocation());
//        if (importantBlock != null) {
//            event.setCancelled(true);
//        }
//    }
//
//    @EventHandler
//    public void fade(BlockFromToEvent event) {
//        ImportantBlock importantBlock = this.fromLocation(event.getBlock().getLocation());
//        if (importantBlock != null) {
//            event.setCancelled(true);
//        }
//    }
//
//    @EventHandler
//    public void spread(BlockSpreadEvent event) {
//        ImportantBlock importantBlock = this.fromLocation(event.getBlock().getLocation());
//        if (importantBlock != null) {
//            event.setCancelled(true);
//        }
//    }
//
//    @EventHandler
//    public void form(BlockFormEvent event) {
//        ImportantBlock importantBlock = this.fromLocation(event.getBlock().getLocation());
//        if (importantBlock != null) {
//            event.setCancelled(true);
//        }
//    }
//
//    @EventHandler
//    public void piston(BlockPistonExtendEvent event) {
//        for (Block block : event.getBlocks()) {
//            ImportantBlock importantBlock = this.fromLocation(block.getLocation());
//            if (importantBlock != null) {
//                event.setCancelled(true);
//            }
//        }
//    }
//
//    @EventHandler
//    public void piston(BlockPistonRetractEvent event) {
//        for (Block block : event.getBlocks()) {
//            ImportantBlock importantBlock = this.fromLocation(block.getLocation());
//            if (importantBlock != null) {
//                event.setCancelled(true);
//            }
//        }
//    }
//

    @EventHandler
    public void interact(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && !event.getPlayer().isSneaking()) {
            return;
        }
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (block == null) {
            return;
        }

        if (block.getState(false) instanceof TileState state) {
            BarterStore store = legacy.getBarterStore(state.getPersistentDataContainer());
            if (store != null && store.getRole(player).hasPermission(BarterPermission.DELETE)) {
                block.setType(Material.AIR);
                event.getPlayer().sendMessage(Component.text("Barrels have been redone to help with item loss! Please recreate your barrel."));
                event.getPlayer().sendMessage(Component.text("NOTE: YOUR ITEMS WILL BE DROPPED ON THE FLOOR... CLICK AGAIN TO CONFIRM"));
                event.getPlayer().getInventory().addItem(ItemInstances.SHOP_LISTER_ITEM.getItem());
                for (ItemStack item : store.getSaleStorage()) {
                    if (item != null) {
                        block.getWorld().dropItemNaturally(block.getLocation(), item);
                    }

                }

                for (ItemStack item : store.getCurrencyStorage()) {
                    if (item != null) {
                        block.getWorld().dropItemNaturally(block.getLocation(), item);
                    }
                }
            }
        }

        BarterManager.INSTANCE.getBarterAndForceTryToLoad(block.getLocation())
                .ifPresent((loadResult) -> {
                    if (loadResult.forceLoading()) {
                        player.sendMessage(Component.text("Please try to click this barrel later! Loading... Note if it still doesnt load please contact an admin!", NamedTextColor.RED));
                        event.setUseInteractedBlock(Event.Result.DENY);
                        return;
                    }

                    loadResult.store().ifPresent((store) -> {
                        Sounds.openChest(player);
                        if (store.getRole(player) == BarterRole.UPKEEPER || player.hasPermission("barterchests.edit_all")) {
                            if (player.isSneaking()) {
                                new BarterBuyGui(player, store).show(player);
                            } else {
                                new BarterGui(store).show(player);
                            }
                            event.setUseInteractedBlock(Event.Result.DENY);
                        } else {
                            new BarterBuyGui(player, store).show(player);
                            event.setUseInteractedBlock(Event.Result.DENY);
                        }
                    });
                });
    }


}
