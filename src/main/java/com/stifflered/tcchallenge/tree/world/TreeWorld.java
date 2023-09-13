package com.stifflered.tcchallenge.tree.world;

import com.stifflered.tcchallenge.tree.blocks.ImportantBlock;
import com.stifflered.tcchallenge.tree.blocks.lookup.GlobalImportantBlockLookup;
import com.stifflered.tcchallenge.tree.childtree.TreeNodeManager;
import com.stifflered.tcchallenge.tree.root.RootManager;
import com.stifflered.tcchallenge.tree.root.type.Root;
import com.stifflered.tcchallenge.util.source.ObjectSource;
import com.stifflered.tcchallenge.util.storage.chunked.ChunkPos;
import com.stifflered.tcchallenge.util.storage.chunked.DataChunk;
import org.bukkit.World;

public interface TreeWorld {

    static TreeWorld of(World world) {
        return TreeWorldRegistry.of(world);
    }

    RootManager getRootManager();

    GlobalImportantBlockLookup globalImportantBlockLookup();

    TreeNodeManager globalNodeManager();

    ObjectSource<ChunkPos, DataChunk<Root>> rootSerializer();

    ObjectSource<ChunkPos, DataChunk<ImportantBlock>> importantBlockSerializer();


}
