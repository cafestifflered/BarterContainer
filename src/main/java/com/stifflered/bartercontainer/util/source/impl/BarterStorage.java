package com.stifflered.bartercontainer.util.source.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stifflered.bartercontainer.barter.serializers.FileBarterSerializer;
import com.stifflered.bartercontainer.barter.serializers.InGameBarterSerializer;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.store.BarterStoreKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;

public class BarterStorage extends SimpleKeyedFileSource<BarterStoreKey, BarterStore> {

    private final FileBarterSerializer barterSerializer = new FileBarterSerializer();

    public BarterStorage() {
        super(Path.of("barter_storage"));
    }

    @Override
    public BarterStoreKey getKey(BarterStore type) {
        return type.getKey();
    }

    @Override
    public Path getChild(BarterStoreKey key, Path parent) {
        return parent.resolve(key.key().toString() + ".json");
    }

    @Override
    public BarterStore loadFromFile(Reader fileReader) throws Exception {
        JsonObject object = (JsonObject) JsonParser.parseReader(fileReader);
        return this.barterSerializer.getBarterStore(object);
    }

    @Override
    public @Nullable BarterStore load(@NotNull BarterStoreKey key) throws Exception {
        BarterStore tree = super.load(key);
        if (tree != null) {
            if (!tree.getKey().equals(key)) {
                throw new IllegalArgumentException("Store key mismatch... this should never happen.");
            }
        }

        return tree;
    }

    @Override
    public boolean saveToFile(BarterStore object, Writer writer) throws Exception {
        JsonObject jsonObject = new JsonObject();
        this.barterSerializer.saveBarterStore(object, jsonObject);
        writer.write(jsonObject.toString());
        return true;
    }
}
