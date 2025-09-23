package com.stifflered.bartercontainer.util.source.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.stifflered.bartercontainer.barter.serializers.FileBarterSerializer;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.store.BarterStoreKey;
import com.stifflered.bartercontainer.util.Messages;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;

/**
 * File-based storage for {@link BarterStore}s keyed by {@link BarterStoreKey}.

 * Storage layout:
 *  - Parent directory: <plugin data folder>/barter_storage
 *  - Per-store file: {UUID}.json

 * Serialization:
 *  - Delegates to {@link FileBarterSerializer} to convert between BarterStore and JsonObject.

 * Safety:
 *  - load(...) verifies that the loaded object's key matches the requested key.
 */
public class BarterStorage extends SimpleKeyedFileSource<BarterStoreKey, BarterStore> {

    /** Serializer that knows how to read/write a store to JSON. */
    private final FileBarterSerializer barterSerializer = new FileBarterSerializer();

    /** Use default "barter_storage" directory under the plugin's data folder. */
    public BarterStorage() {
        super(Path.of("barter_storage"));
    }

    /** Extract the logical key for a store. */
    @Override
    public BarterStoreKey getKey(BarterStore type) {
        return type.getKey();
    }

    /** Path convention for a given key: {UUID}.json */
    @Override
    public Path getChild(BarterStoreKey key, Path parent) {
        return parent.resolve(key.key().toString() + ".json");
    }

    /** Parse JSON from disk into a BarterStore via FileBarterSerializer. */
    @Override
    public BarterStore loadFromFile(Reader fileReader) {
        JsonObject object = (JsonObject) JsonParser.parseReader(fileReader);
        return this.barterSerializer.getBarterStore(object);
    }

    /**
     * Ensures key integrity: if a store is loaded, its embedded key must match the file key requested.
     * Returns null if the file does not exist.
     */
    @Override
    public @Nullable BarterStore load(@NotNull BarterStoreKey key) throws Exception {
        BarterStore tree = super.load(key);
        if (tree != null) {
            if (!tree.getKey().equals(key)) {
                throw new IllegalArgumentException(Messages.fmt(
                        "storage.barter.key_mismatch",
                        "expected", key.toString(),
                        "actual", tree.getKey().toString()
                ));
            }
        }

        return tree;
    }

    /** Serialize a store into JSON and write it out. */
    @Override
    public boolean saveToFile(BarterStore object, Writer writer) throws Exception {
        JsonObject jsonObject = new JsonObject();
        this.barterSerializer.saveBarterStore(object, jsonObject);
        writer.write(jsonObject.toString());
        return true;
    }
}
