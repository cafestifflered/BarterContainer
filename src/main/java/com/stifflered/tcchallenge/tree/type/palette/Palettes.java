package com.stifflered.tcchallenge.tree.type.palette;

import org.bukkit.Material;

import java.util.Set;

public final class Palettes {

    public static final TreePalette OAK = new TreePalette(
            "Oak Tree",
            Material.OAK_SAPLING,
            Material.OAK_LOG,
            Material.OAK_WOOD,
            Set.of(Material.OAK_LOG, Material.OAK_WOOD, Material.STRIPPED_OAK_LOG, Material.STRIPPED_OAK_WOOD),
            Material.OAK_LEAVES,
            Material.GRASS
    );

    public static final TreePalette BIRCH = new TreePalette(
            "Birch Tree",
            Material.BIRCH_SAPLING,
            Material.BIRCH_LOG,
            Material.BIRCH_WOOD,
            Set.of(Material.BIRCH_LOG, Material.BIRCH_WOOD, Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_BIRCH_WOOD),
            Material.BIRCH_LEAVES,
            Material.OXEYE_DAISY
    );

    public static final TreePalette SPRUCE = new TreePalette(
            "Spruce Tree",
            Material.SPRUCE_SAPLING,
            Material.SPRUCE_LOG,
            Material.SPRUCE_WOOD,
            Set.of(Material.SPRUCE_LOG, Material.SPRUCE_WOOD, Material.STRIPPED_SPRUCE_LOG, Material.STRIPPED_SPRUCE_WOOD),
            Material.SPRUCE_LEAVES,
            Material.PODZOL
    );

    public static final TreePalette JUNGLE = new TreePalette(
            "Jungle Tree",
            Material.JUNGLE_SAPLING,
            Material.JUNGLE_LOG,
            Material.JUNGLE_WOOD,
            Set.of(Material.JUNGLE_LOG, Material.JUNGLE_WOOD, Material.STRIPPED_JUNGLE_LOG, Material.STRIPPED_JUNGLE_WOOD),
            Material.JUNGLE_LEAVES,
            Material.COCOA_BEANS
    );

    public static final TreePalette ACACIA = new TreePalette(
            "Acacia Tree",
            Material.ACACIA_SAPLING,
            Material.ACACIA_LOG,
            Material.ACACIA_WOOD,
            Set.of(Material.ACACIA_LOG, Material.ACACIA_WOOD, Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_ACACIA_WOOD),
            Material.ACACIA_LEAVES,
            Material.RED_SAND
    );

    public static final TreePalette DARK_OAK = new TreePalette(
            "Dark Oak Tree",
            Material.DARK_OAK_SAPLING,
            Material.DARK_OAK_LOG,
            Material.DARK_OAK_WOOD,
            Set.of(Material.DARK_OAK_LOG, Material.DARK_OAK_WOOD, Material.STRIPPED_DARK_OAK_LOG, Material.STRIPPED_DARK_OAK_WOOD),
            Material.DARK_OAK_LEAVES,
            Material.RED_MUSHROOM
    );

    public static final TreePalette MANGROVE = new TreePalette(
            "Mangrove Tree",
            Material.MANGROVE_PROPAGULE,
            Material.MANGROVE_LOG,
            Material.MANGROVE_WOOD,
            Set.of(Material.MANGROVE_LOG, Material.MANGROVE_WOOD, Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_MANGROVE_WOOD, Material.MANGROVE_ROOTS, Material.MUDDY_MANGROVE_ROOTS),
            Material.MANGROVE_LEAVES,
            Material.MUD
    );

    public static final TreePalette AZALEA = new TreePalette(
            "Azalea Tree",
            Material.FLOWERING_AZALEA_LEAVES,
            Material.OAK_LOG,
            Material.OAK_WOOD,
            Set.of(Material.OAK_LOG, Material.OAK_WOOD, Material.STRIPPED_OAK_LOG, Material.STRIPPED_OAK_WOOD),
            Material.FLOWERING_AZALEA_LEAVES,
            Material.FLOWERING_AZALEA_LEAVES
    );

    public static final TreePalette CRIMSON_FUNGUS = new TreePalette(
            "Crimson Fungus",
            Material.CRIMSON_FUNGUS,
            Material.CRIMSON_STEM,
            Material.CRIMSON_HYPHAE,
            Set.of(Material.CRIMSON_STEM, Material.CRIMSON_HYPHAE, Material.STRIPPED_CRIMSON_STEM, Material.STRIPPED_CRIMSON_HYPHAE),
            Material.NETHER_WART_BLOCK,
            Material.CRIMSON_ROOTS
    );

    public static final TreePalette WARPED_FUNGUS = new TreePalette(
            "Warped Fungus",
            Material.WARPED_FUNGUS,
            Material.WARPED_STEM,
            Material.WARPED_HYPHAE,
            Set.of(Material.WARPED_STEM, Material.WARPED_HYPHAE, Material.STRIPPED_WARPED_STEM, Material.STRIPPED_WARPED_HYPHAE),
            Material.WARPED_WART_BLOCK,
            Material.WARPED_ROOTS
    );

}
