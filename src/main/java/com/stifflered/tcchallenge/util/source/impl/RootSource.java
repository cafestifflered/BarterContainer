package com.stifflered.tcchallenge.util.source.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stifflered.tcchallenge.tree.root.type.Root;
import com.stifflered.tcchallenge.util.serializers.root.RootChunkSerializer;
import com.stifflered.tcchallenge.util.storage.chunked.ChunkPos;
import com.stifflered.tcchallenge.util.storage.chunked.DataChunk;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;

// Storage format:
// Minecraft chunk storage like system? Palletted container, with a palette being the tree types in the area.
public class RootSource extends SimpleKeyedFileSource<ChunkPos, DataChunk<Root>> {

    public RootSource(Path parent) {
        super(parent);
    }

    @Override
    public ChunkPos getKey(DataChunk<Root> type) {
        return type.getPos();
    }

    @Override
    public Path getChild(ChunkPos key, Path parent) {
        return parent.resolve(key.x() + "x" + key.z() + "z.json");
    }

    @Override
    public DataChunk<Root> loadFromFile(Reader fileReader) {
        JsonObject object = (JsonObject) JsonParser.parseReader(fileReader);
        return RootChunkSerializer.INSTANCE.decode(object);
    }

    @Override
    public boolean saveToFile(DataChunk<Root> object, Writer writer) throws Exception {
        writer.write(RootChunkSerializer.INSTANCE.encode(object).toString());
        return true;
    }
}
