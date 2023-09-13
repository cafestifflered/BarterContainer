package com.stifflered.tcchallenge.tree.world;

import com.stifflered.tcchallenge.tree.blocks.ImportantBlock;
import com.stifflered.tcchallenge.tree.blocks.lookup.GlobalImportantBlockLookup;
import com.stifflered.tcchallenge.tree.childtree.GlobalTreeNodeManager;
import com.stifflered.tcchallenge.tree.childtree.TreeNodeManager;
import com.stifflered.tcchallenge.tree.root.RootManager;
import com.stifflered.tcchallenge.tree.root.type.Root;
import com.stifflered.tcchallenge.util.source.ObjectSource;
import com.stifflered.tcchallenge.util.storage.chunked.ChunkPos;
import com.stifflered.tcchallenge.util.storage.chunked.DataChunk;

public record SimpleTreeWorld(RootManager rootManager,
                              GlobalImportantBlockLookup globalImportantBlockLookup,
                              GlobalTreeNodeManager globalTreeNodeManager,
                              ObjectSource<ChunkPos, DataChunk<Root>> rootSerializer,
                              ObjectSource<ChunkPos, DataChunk<ImportantBlock>> importantBlockSerializer
) implements TreeWorld {

    @Override
    public RootManager getRootManager() {
        return this.rootManager;
    }

    @Override
    public GlobalImportantBlockLookup globalImportantBlockLookup() {
        return this.globalImportantBlockLookup;
    }

    @Override
    public TreeNodeManager globalNodeManager() {
        return this.globalTreeNodeManager;
    }

    @Override
    public ObjectSource<ChunkPos, DataChunk<Root>> rootSerializer() {
        return this.rootSerializer;
    }

    @Override
    public ObjectSource<ChunkPos, DataChunk<ImportantBlock>> importantBlockSerializer() {
        return this.importantBlockSerializer;
    }
}
