package com.stifflered.tcchallenge.listeners;

import com.stifflered.tcchallenge.BarterChallenge;
import com.stifflered.tcchallenge.tree.Tree;
import com.stifflered.tcchallenge.tree.TreeManager;
import com.stifflered.tcchallenge.tree.blocks.ImportantBlock;
import com.stifflered.tcchallenge.tree.blocks.lookup.GlobalImportantBlockLookup;
import com.stifflered.tcchallenge.tree.root.type.Root;
import com.stifflered.tcchallenge.tree.world.TreeWorld;
import com.stifflered.tcchallenge.util.source.Sources;
import com.stifflered.tcchallenge.util.storage.chunked.ChunkPos;
import com.stifflered.tcchallenge.util.storage.chunked.DataChunk;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldSaveEvent;

import java.util.logging.Level;

public class ChunkStorageListener implements Listener {

    public static void saveAll() {
        BarterChallenge.INSTANCE.getLogger().log(Level.INFO, "Saving TCChallenge Trees");
        save("trees", () -> {
            for (Tree tree : TreeManager.INSTANCE.getLoadedTrees()) {
                if (tree != null) {
                    try {
                        Sources.TREE_SOURCE.save(tree);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to save save tree (%s)".formatted(tree.getKey().toString()), e);
                    }
                }
            }
        });

        for (World world : Bukkit.getWorlds()) {
            saveWorld(world);
        }
    }

    public static void saveWorld(World world) {
        BarterChallenge.INSTANCE.getLogger().log(Level.INFO, "Saving TCChallenge Assets for " + world.getKey());

        TreeWorld treeWorld = TreeWorld.of(world);

        save("root chunks", () -> {
            Long2ObjectOpenHashMap.FastEntrySet<DataChunk<Root>> rootChunkSet = treeWorld.getRootManager().getStorage().chunkEntries();

            for (Long2ObjectMap.Entry<DataChunk<Root>> entry : rootChunkSet) {
                DataChunk<Root> chunk = entry.getValue();
                try {
                    if (chunk.isDirty()) {
                        treeWorld.rootSerializer().save(chunk);
                        chunk.setDirty(false);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to save root chunk (%s)".formatted(chunk.toString()), e);
                }
            }
        });

        save("special block chunks", () -> {
            Long2ObjectMap.FastEntrySet<DataChunk<ImportantBlock>> rootChunkSet = treeWorld.globalImportantBlockLookup().getStorage().chunkEntries();

            for (Long2ObjectMap.Entry<DataChunk<ImportantBlock>> entry : rootChunkSet) {
                DataChunk<ImportantBlock> chunk = entry.getValue();
                try {
                    if (chunk.isDirty()) {
                        treeWorld.importantBlockSerializer().save(chunk);
                        chunk.setDirty(false);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to save important block chunk (%s)".formatted(chunk.toString()), e);
                }
            }
        });
        BarterChallenge.INSTANCE.getLogger().log(Level.INFO, "Finished saving TCChallenge assets");
    }

    @EventHandler
    public void chunkLoadEvent(ChunkLoadEvent event) {
        World world = event.getWorld();
        Chunk chunk = event.getChunk();
        ChunkPos pos = new ChunkPos(chunk.getX(), chunk.getZ());

        TreeWorld treeWorld = TreeWorld.of(world);
        try {
            DataChunk<Root> rootChunk = treeWorld.rootSerializer().load(pos);
            if (rootChunk == null) {
                return;
            }

            TreeWorld.of(event.getWorld()).getRootManager().getStorage().addChunk(chunk.getX(), chunk.getZ(), rootChunk);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save root chunk (%s)".formatted(pos), e);
        }

        try {
            DataChunk<ImportantBlock> rootChunk = treeWorld.importantBlockSerializer().load(pos);
            if (rootChunk == null) {
                return;
            }

            GlobalImportantBlockLookup importantBlockLookup = TreeWorld.of(event.getWorld()).globalImportantBlockLookup();
            importantBlockLookup.addChunk(rootChunk);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save root chunk (%s)".formatted(pos), e);
        }
    }

    @EventHandler
    public void chunkUnloadEvent(ChunkUnloadEvent event) {
        World world = event.getWorld();
        Chunk chunk = event.getChunk();
        TreeWorld treeWorld = TreeWorld.of(world);

        {
            DataChunk<Root> rootChunk = TreeWorld.of(event.getWorld()).getRootManager().getStorage().deleteChunk(chunk.getX(), chunk.getZ());
            if (rootChunk == null) {
                return;
            }

            try {
                treeWorld.rootSerializer().save(rootChunk);
            } catch (Exception e) {
                throw new RuntimeException("Failed to save root chunk (%s)".formatted(rootChunk.getPos().toString()), e);
            }
        }

        {
            DataChunk<ImportantBlock> rootChunk = TreeWorld.of(event.getWorld()).globalImportantBlockLookup().deleteChunk(chunk.getX(), chunk.getZ());
            if (rootChunk == null) {
                return;
            }

            try {
                treeWorld.importantBlockSerializer().save(rootChunk);
            } catch (Exception e) {
                throw new RuntimeException("Failed to save root chunk (%s)".formatted(rootChunk.getPos().toString()), e);
            }
        }
    }

    @EventHandler
    public void saveEvent(WorldSaveEvent event) {
        saveWorld(event.getWorld());
    }

    private static void save(String name, Runnable runnable) {
        long capture = System.currentTimeMillis();
        runnable.run();
        BarterChallenge.INSTANCE.getLogger().log(Level.INFO, "Saved " + name + " in " + (System.currentTimeMillis() - capture) + "ms!");
    }

}
