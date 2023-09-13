package com.stifflered.tcchallenge.util.serializers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.stifflered.tcchallenge.tree.blocks.ImportantBlock;
import com.stifflered.tcchallenge.tree.blocks.ImportantBlockType;
import com.stifflered.tcchallenge.util.storage.chunked.ChunkPos;
import com.stifflered.tcchallenge.util.storage.chunked.DataChunk;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

public final class ImportantBlockChunkSerializer implements Codec<DataChunk<ImportantBlock>, JsonObject> {

    public static final ImportantBlockChunkSerializer INSTANCE = new ImportantBlockChunkSerializer();
    public static final int VERSION = 0;

    private static final Serializer<JsonObject, DataChunk<ImportantBlock>> CHUNK_DESERIALIZER = (from) -> {
        ChunkPos pos = new ChunkPos(from.get("x").getAsInt(), from.get("z").getAsInt());
        DataChunk<ImportantBlock> dataChunk = new DataChunk<>(pos);

        {
            JsonArray specialBlockArray = from.getAsJsonArray("special_blocks");
            for (JsonElement key : specialBlockArray) {
                JsonObject rootObject = key.getAsJsonObject();
                Codec<? extends ImportantBlock, JsonObject> codec = ImportantBlockType.fromIdentifier(rootObject.get("id").getAsString());

                ImportantBlock importantBlock = codec.decode(rootObject);
                dataChunk.addData(importantBlock.getLocation(), importantBlock);
            }
        }

        return dataChunk;
    };

    @SuppressWarnings("unchecked")
    private static final Serializer<DataChunk<ImportantBlock>, JsonObject> CHUNK_SERIALIZER = (from) -> {
        JsonObject object = new JsonObject();
        JsonArray blocks = new JsonArray();

        object.addProperty("x", from.getPos().x());
        object.addProperty("z", from.getPos().z());

        for (Long2ObjectMap.Entry<ImportantBlock> root : from.getData().long2ObjectEntrySet()) {
            ImportantBlockType type = root.getValue().getType();
            Codec<ImportantBlock, JsonObject> codec = (Codec<ImportantBlock, JsonObject>) type.getCodec();

            JsonObject data = codec.encode(root.getValue());
            data.addProperty("id", type.getIdentifier());

            blocks.add(data);
        }
        object.add("special_blocks", blocks);

        return object;
    };

    @Override
    public JsonObject encode(DataChunk<ImportantBlock> from) {
        JsonObject object = CHUNK_SERIALIZER.serialize(from);
        object.addProperty("version", VERSION);

        return object;
    }

    @Override
    public DataChunk<ImportantBlock> decode(JsonObject type) {
        return CHUNK_DESERIALIZER.serialize(type);
    }
}
