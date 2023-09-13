package com.stifflered.tcchallenge.util.source.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stifflered.tcchallenge.tree.blocks.ImportantBlock;
import com.stifflered.tcchallenge.util.serializers.ImportantBlockChunkSerializer;
import com.stifflered.tcchallenge.util.storage.chunked.ChunkPos;
import com.stifflered.tcchallenge.util.storage.chunked.DataChunk;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;

public class ImportantBlockSource extends SimpleKeyedFileSource<ChunkPos, DataChunk<ImportantBlock>> {

    public ImportantBlockSource(Path path) {
        super(path);
    }

    @Override
    public ChunkPos getKey(DataChunk<ImportantBlock> type) {
        return type.getPos();
    }

    @Override
    public Path getChild(ChunkPos key, Path parent) {
        return parent.resolve(key.x() + "x" + key.z() + "z.json");
    }

    @Override
    public DataChunk<ImportantBlock> loadFromFile(Reader fileReader) {
        JsonObject object = (JsonObject) JsonParser.parseReader(fileReader);
        return ImportantBlockChunkSerializer.INSTANCE.decode(object);
    }

    @Override
    public boolean saveToFile(DataChunk<ImportantBlock> object, Writer writer) throws Exception {
        writer.write(ImportantBlockChunkSerializer.INSTANCE.encode(object).toString());
        return true;
    }
}
