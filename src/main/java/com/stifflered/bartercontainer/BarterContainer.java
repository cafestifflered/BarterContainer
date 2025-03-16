package com.stifflered.bartercontainer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.barter.ChunkBarterStorage;
import com.stifflered.bartercontainer.command.*;
import com.stifflered.bartercontainer.item.ItemInstances;
import com.stifflered.bartercontainer.listeners.*;
import com.stifflered.bartercontainer.player.ShoppingListManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEvent;
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class BarterContainer extends JavaPlugin implements Listener {

    public static final String adminTreeName = "The TreeCrafter";

    public static BarterContainer INSTANCE;
    private ChunkBarterStorage chunkBarterStorage;
    private BarterContainerConfiguration configuration;
    private ShoppingListManager shoppingListManager;

    @Override
    public void onEnable() {
        INSTANCE = this;
        chunkBarterStorage = new ChunkBarterStorage(BarterManager.INSTANCE);
        this.shoppingListManager = new ShoppingListManager(this);
        Bukkit.getCommandMap().register("barterbarrels", new BarterContainerCommand());
        Bukkit.getCommandMap().register("catalog", new CatalogueCommand());
        Bukkit.getCommandMap().register("shopping-list", new ShoppingListCommand());
        Bukkit.getCommandMap().register("end-tracking", new EndTrackingCommand());


        this.register(
                new ItemInstanceListener(),
                new JoinEventListener(),
                new SafeFireworkDamageListener(),
                new BarterBlockListener(),
                new BarterInventoryListener(),
                new ChunkListener(chunkBarterStorage)
        );


        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            //worldGuardHook = new WorldGuardHook();
            //log("Hooked into World Guard");
        }
        this.configuration = new BarterContainerConfiguration(this);
        ItemInstances.SHOP_LISTER_ITEM.getItem(); // Load ITEM_INSTANCES
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

    public BarterContainerConfiguration getConfiguration() {
        return configuration;
    }

    public ShoppingListManager getShoppingListManager() {
        return shoppingListManager;
    }
}
