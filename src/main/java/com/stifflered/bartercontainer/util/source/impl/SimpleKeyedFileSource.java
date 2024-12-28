package com.stifflered.bartercontainer.util.source.impl;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.util.source.ObjectSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public abstract class SimpleKeyedFileSource<K, T> implements ObjectSource<K, T> {

    private final Path parent;

    public SimpleKeyedFileSource(Path path) {
        this.parent = BarterContainer.INSTANCE.getDataFolder().toPath().resolve(path);

        try {
            if (Files.notExists(this.parent)) {
                Files.createDirectories(this.parent);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public abstract K getKey(T type);

    public abstract Path getChild(K key, Path parent);

    public abstract T loadFromFile(Reader fileReader) throws Exception;

    @Override
    public @Nullable T load(@NotNull K key) throws Exception {
        Path file = this.getChild(key, this.parent);
        if (Files.notExists(file)) {
            return null;
        }

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            return this.loadFromFile(reader);
        }
    }

    @Override
    public boolean delete(@NotNull T type) throws Exception {
        Path file = this.getChild(this.getKey(type), this.parent);
        if (Files.notExists(file)) {
            return false;
        }

        Files.delete(file);
        return true;
    }

    public abstract boolean saveToFile(T object, Writer writer) throws Exception;

    @Override
    public boolean save(@NotNull T type) throws Exception {
        Path file = this.getChild(this.getKey(type), this.parent);
        if (Files.notExists(file)) {
            Files.createFile(file);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            return this.saveToFile(type, writer);
        }
    }

    @Override
    public List<T> getAll() throws Exception {
        return Files.list(this.parent).map((file) -> {
            try (BufferedReader reader = Files.newBufferedReader(file)) {
                return this.loadFromFile(reader);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).toList();
    }
}
