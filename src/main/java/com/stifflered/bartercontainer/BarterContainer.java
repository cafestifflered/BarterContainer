package com.stifflered.bartercontainer;

import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.command.BarterContainerCommand;
import com.stifflered.bartercontainer.item.ItemInstances;
import com.stifflered.bartercontainer.listeners.*;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class BarterContainer extends JavaPlugin implements Listener {

    public static final String adminTreeName = "The TreeCrafter";

    public static JavaPlugin INSTANCE;

    @Override
    public void onEnable() {
        INSTANCE = this;
        ItemInstances.SHOP_LISTER_ITEM.getItem(); // Load ITEM_INSTANCES
        Bukkit.getCommandMap().register("barterbarrels", new BarterContainerCommand());

        this.register(
                new ItemInstanceListener(),
                new JoinEventListener(),
                new SafeFireworkDamageListener(),
                new BarterBlockListener(),
                new BarterInventoryListener()
        );

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderAPIHook();
        }


        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            //worldGuardHook = new WorldGuardHook();
            //log("Hooked into World Guard");
        }
    }

//    @EventHandler
//    public void interact(PlayerInteractEvent event) {
//        if (event.getHand() == EquipmentSlot.HAND && event.getPlayer().isSneaking()) {
//            BlockScannerContext context = new BlockScannerContext();
//            new BlockScanner(new MaterialSetTag(NamespacedKey.fromString("hi:hi")).add(MaterialSetTag.LOGS)).compile(event.getClickedBlock().getLocation(), context).thenRun(() -> {
//                event.getPlayer().sendMessage("TADA");
//                for (Location location : context.compiled) {
//                    event.getPlayer().sendBlockChange(location, Bukkit.getServer().createBlockData(Material.COBBLED_DEEPSLATE));
//                }
//                Logger.getLogger("E").info(String.valueOf(context.compiled.size()));
//                this.locationList = context.compiled;
//            });
//        }
//    }

    @Override
    public void onDisable() {
        BarterManager.INSTANCE.saveAll();
    }

    // TODO
    public boolean isChallengeWorld(World world) {
        return true;
    }

    private void register(Listener... listeners) {
        for (Listener listener : listeners) {
            Bukkit.getPluginManager().registerEvents(listener, this);
        }
    }

}
