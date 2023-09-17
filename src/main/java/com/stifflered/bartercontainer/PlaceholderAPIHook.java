package com.stifflered.bartercontainer;

import me.clip.placeholderapi.external.EZPlaceholderHook;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;

public class PlaceholderAPIHook extends EZPlaceholderHook {

    private static final SimpleDateFormat FORMAT_DATE_TIME = new SimpleDateFormat("yyyy MM dd HH:mm:ss");
    private static final SimpleDateFormat FORMAT_DATE = new SimpleDateFormat("yyyy MM dd");


    public PlaceholderAPIHook() {
        super(BarterContainer.INSTANCE, "bartercontainer");

        this.hook();
    }

    @Override
    public String onPlaceholderRequest(Player player, String s) {

        return "";
    }
}
