package com.stifflered.tcchallenge;

import com.stifflered.tcchallenge.tree.OnlineTree;
import com.stifflered.tcchallenge.tree.TreeManager;
import com.stifflered.tcchallenge.tree.components.StatsStorage;
import com.stifflered.tcchallenge.tree.components.TreeComponent;
import com.stifflered.tcchallenge.util.StringUtil;
import me.clip.placeholderapi.external.EZPlaceholderHook;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;

public class PlaceholderAPIHook extends EZPlaceholderHook {

    private static final SimpleDateFormat FORMAT_DATE_TIME = new SimpleDateFormat("yyyy MM dd HH:mm:ss");
    private static final SimpleDateFormat FORMAT_DATE = new SimpleDateFormat("yyyy MM dd");


    public PlaceholderAPIHook() {
        super(BarterChallenge.INSTANCE, "tcchallenge");

        this.hook();
    }

    @Override
    public String onPlaceholderRequest(Player player, String s) {
        OnlineTree tree = TreeManager.INSTANCE.getTreeIfLoaded(player);

        if (s.equalsIgnoreCase("treetype")) {
            return tree != null ? tree.getType().toString().toLowerCase() : "None";
        }
        if (s.equalsIgnoreCase("treename")) {
            return tree != null ? PlainTextComponentSerializer.plainText().serialize(tree.getNameStyled()) : "None";
        }

        if (s.equalsIgnoreCase("pylons")) {
            // return tree != null ? tree.getStorage().get(TreeComponent.MEMBER_STORAGE) + "" : "0";
        }

        if (s.equalsIgnoreCase("age")) {
            if (tree == null) {
                return "0";
            }

            StatsStorage statsStorage = tree.getComponentStorage().get(TreeComponent.STATS);
            long timeSinceCreated = System.currentTimeMillis() - statsStorage.getCreated().toEpochMilli();

            return StringUtil.formatMilliDuration(timeSinceCreated);
        }

        if (s.equalsIgnoreCase("date")) {
            if (tree == null) {
                return "0";
            }

            StatsStorage statsStorage = tree.getComponentStorage().get(TreeComponent.STATS);

            return FORMAT_DATE.format(statsStorage.getCreated());
        }

        if (s.equalsIgnoreCase("dateandtime")) {
            if (tree == null) {
                return "0";
            }

            StatsStorage statsStorage = tree.getComponentStorage().get(TreeComponent.STATS);

            return FORMAT_DATE_TIME.format(statsStorage.getCreated());
        }

        if (s.equalsIgnoreCase("weather")) {
            World w = player.getWorld();

            return w.hasStorm() ? (w.isThundering() ? "Thundering" : "Raining") : "Sunny";
        }


        return "";
    }
}
