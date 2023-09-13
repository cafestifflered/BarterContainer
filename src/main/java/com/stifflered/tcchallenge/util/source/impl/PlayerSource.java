package com.stifflered.tcchallenge.util.source.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stifflered.tcchallenge.store.BarterStore;
import com.stifflered.tcchallenge.store.BarterStoreKey;
import com.stifflered.tcchallenge.tree.Tree;
import com.stifflered.tcchallenge.tree.TreeKey;
import com.stifflered.tcchallenge.util.serializers.tree.TreeSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;

public class PlayerSource extends SimpleKeyedFileSource<BarterStoreKey, BarterStore> {

    public PlayerSource() {
        super(Path.of("stores"));
    }

    @Override
    public BarterStoreKey getKey(BarterStore type) {
        return type.getKey();
    }

    @Override
    public Path getChild(BarterStoreKey key, Path parent) {
        return parent.resolve(key.asFileName() + ".json");
    }

    @Override
    public BarterStore loadFromFile(Reader fileReader) throws Exception {
        JsonObject object = (JsonObject) JsonParser.parseReader(fileReader);
        return TreeSerializer.INSTANCE.decode(object);
    }


    @Override
    public boolean saveToFile(BarterStore object, Writer writer) throws Exception {
        writer.write(TreeSerializer.INSTANCE.encode(object).toString());
        return true;
    }
}
