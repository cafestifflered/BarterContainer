package com.stifflered.tcchallenge.tree.type;

import com.stifflered.tcchallenge.item.impl.TreeSaplingItemInstance;
import com.stifflered.tcchallenge.tree.type.loghandler.LogHandler;
import com.stifflered.tcchallenge.tree.type.loghandler.SimpleLogPlacer;
import com.stifflered.tcchallenge.tree.type.palette.Palettes;
import com.stifflered.tcchallenge.tree.type.palette.TreePalette;
import org.bukkit.inventory.ItemStack;

public enum TreeType {

    OAK(Palettes.OAK),
    AZALEA(Palettes.AZALEA),
    SPRUCE(Palettes.SPRUCE),
    BIRCH(Palettes.BIRCH),
    JUNGLE(Palettes.JUNGLE),
    ACACIA(Palettes.ACACIA),
    DARK_OAK(Palettes.DARK_OAK),
    MANGROVE(Palettes.MANGROVE),

    CRIMSON_FUNGUS(Palettes.CRIMSON_FUNGUS, TreeSpecies.NETHER),
    WARPED_FUNGUS(Palettes.WARPED_FUNGUS, TreeSpecies.NETHER),
    ;

    private final TreePalette palette;
    private final LogHandler placer;
    private final TreeSpecies species;

    private final TreeSaplingItemInstance itemInstance;

    TreeType(TreePalette palette) {
        this(palette, new SimpleLogPlacer(palette), TreeSpecies.OVERWORLD);
    }

    TreeType(TreePalette palette, LogHandler placer) {
        this(palette, placer, TreeSpecies.OVERWORLD);
    }

    TreeType(TreePalette palette, TreeSpecies species) {
        this(palette, new SimpleLogPlacer(palette), species);
    }

    TreeType(TreePalette palette, LogHandler logPlacer, TreeSpecies species) {
        this.palette = palette;
        this.placer = logPlacer;
        this.species = species;
        this.itemInstance = new TreeSaplingItemInstance(this);
    }

    public LogHandler getPlacer() {
        return this.placer;
    }

    public TreeSpecies getSpecies() {
        return this.species;
    }

    public TreePalette getPalette() {
        return this.palette;
    }

    public ItemStack getSaplingItem() {
        return this.itemInstance.getItem();
    }
}
