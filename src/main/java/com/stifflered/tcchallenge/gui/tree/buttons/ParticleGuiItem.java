package com.stifflered.tcchallenge.gui.tree.buttons;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.stifflered.tcchallenge.gui.common.SimplePaginator;
import com.stifflered.tcchallenge.tree.OnlineTree;
import com.stifflered.tcchallenge.tree.components.TreeComponent;
import com.stifflered.tcchallenge.tree.components.cosmetic.CosmeticStorage;
import com.stifflered.tcchallenge.tree.components.cosmetic.particle.Particle;
import com.stifflered.tcchallenge.tree.components.cosmetic.particle.ParticleRegistry;
import com.stifflered.tcchallenge.util.Components;
import com.stifflered.tcchallenge.util.ItemUtil;
import com.stifflered.tcchallenge.util.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ParticleGuiItem extends GuiItem {

    private static final ComponentHolder HOLDER = ComponentHolder.of(
            Component.text("Particle Effects")
    );

    private static final Component SELECTED = Component.text("Selected! ", NamedTextColor.GREEN, TextDecoration.ITALIC);

    public ParticleGuiItem(OnlineTree tree) {
        super(ItemUtil.wrapEdit(new ItemStack(Material.NETHER_STAR), (meta) -> {
            Components.name(meta, Component.text("☄ Particle Effect", NamedTextColor.AQUA));
            Components.lore(meta, Components.miniSplit("""
                    <gray>Click to <green>change</green> your tree particle effect.
                                        
                    <gray>Chosen Particle:</gray> <particle>
                    """, Placeholder.component("particle", getChosenParticle(tree))));
        }), (event) -> {
            List<GuiItem> items = new ArrayList<>();
            Particle target = tree.getComponentStorage().get(TreeComponent.COSMETIC).getParticle();

            for (Particle particle : ParticleRegistry.getParticleMap().values()) {
                ItemStack itemStack = ItemUtil.wrapEdit(new ItemStack(particle.getMaterial()), (meta) -> {
                    if (particle == target) {
                        Components.name(meta, SELECTED.append(particle.getName()));
                    } else {
                        Components.name(meta, particle.getName());
                    }

                    Components.lore(meta, particle.getDescription());
                });

                GuiItem guiItem = new GuiItem(itemStack, (click) -> {
                    CosmeticStorage cosmeticStorage = tree.getComponentStorage().get(TreeComponent.COSMETIC);
                    cosmeticStorage.setParticle(particle);
                    click.getWhoClicked().closeInventory();
                    Sounds.choose(click.getWhoClicked());
                });
                items.add(guiItem);
            }

            SimplePaginator paginator = new SimplePaginator(6, HOLDER, items, event.getClickedInventory());
            paginator.show(event.getWhoClicked());
        });
    }

    private static Component getChosenParticle(OnlineTree tree) {
        Particle particle = tree.getComponentStorage().get(TreeComponent.COSMETIC).getParticle();
        if (particle == null) {
            return Component.text("None");
        } else {
            return particle.getName();
        }
    }

}
