package com.stifflered.tcchallenge;

import com.stifflered.tcchallenge.command.TCChallengeCommand;
import com.stifflered.tcchallenge.command.TCDebugCommand;
import com.stifflered.tcchallenge.command.TPHeartCommand;
import com.stifflered.tcchallenge.events.PlayerTickEvent;
import com.stifflered.tcchallenge.item.ItemInstances;
import com.stifflered.tcchallenge.listeners.DamageEventListener;
import com.stifflered.tcchallenge.listeners.InteractEventListener;
import com.stifflered.tcchallenge.listeners.ItemInstanceListener;
import com.stifflered.tcchallenge.listeners.JoinEventListener;
import com.stifflered.tcchallenge.listeners.LogListener;
import com.stifflered.tcchallenge.listeners.PlayerPreJoinEventListener;
import com.stifflered.tcchallenge.listeners.PlayerQuitEventListener;
import com.stifflered.tcchallenge.listeners.ProtectedBlockListener;
import com.stifflered.tcchallenge.listeners.RespawnEventListener;
import com.stifflered.tcchallenge.listeners.ChunkStorageListener;
import com.stifflered.tcchallenge.listeners.SafeFireworkDamageListener;
import com.stifflered.tcchallenge.listeners.TreeGrowEventListener;
import com.stifflered.tcchallenge.listeners.TreePlantModeListener;
import com.stifflered.tcchallenge.listeners.WorldEventHandler;
import com.stifflered.tcchallenge.tree.TreeManager;
import com.stifflered.tcchallenge.tree.abilities.AbilityManager;
import com.stifflered.tcchallenge.tree.mechanics.PlayerTreeWitherManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class BarterChallenge extends JavaPlugin implements Listener {

    public static final String adminTreeName = "The TreeCrafter";

    public static JavaPlugin INSTANCE;
    public static WorldGuardHook worldGuardHook;

    @Override
    public void onEnable() {
        INSTANCE = this;
        Bukkit.getCommandMap().register("tcdebug", new TCDebugCommand());
        Bukkit.getCommandMap().register("tcchallenge", new TCChallengeCommand());
        Bukkit.getCommandMap().register("tpheart", new TPHeartCommand());

        this.register(
                new PlayerPreJoinEventListener(),
                new PlayerQuitEventListener(),
                new LogListener(),
                new DamageEventListener(),
                new ChunkStorageListener(),
                new ItemInstanceListener(),
                new JoinEventListener(),
                new SafeFireworkDamageListener(),
                new InteractEventListener()
        );


        // Tree abilities
        AbilityManager.init();

        new PlayerTreeWitherManager();


        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderAPIHook();
        }


        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            //worldGuardHook = new WorldGuardHook();
            //log("Hooked into World Guard");
        }

        ItemInstances.TREE_SELECTOR_ITEM_INSTANCE.getItem(); // Load
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
        ChunkStorageListener.saveAll();
        TreeManager.INSTANCE.getExecutor().shutdown();
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
