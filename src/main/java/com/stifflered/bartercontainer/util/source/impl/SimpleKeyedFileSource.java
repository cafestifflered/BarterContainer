package com.stifflered.bartercontainer.util.source.impl;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.util.source.ObjectSource;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

/**
 * Generic helper for file-backed object sources keyed by some K.

 * Responsibilities:
 *  - Establish a parent directory under the plugin's data folder.
 *  - Define abstract hooks for key extraction, path resolution, and object (de)serialization.
 *  - Provide load/save/delete/getAll implementations using java.nio file APIs.

 * Threading:
 *  - Methods are synchronous; callers should dispatch to async threads when performing heavy I/O.
 */
public abstract class SimpleKeyedFileSource<K, T> implements ObjectSource<K, T> {

    /** Parent directory where files for this source live. */
    private final Path parent;

    /**
     * @param path child path (relative) under the plugin data folder, e.g. "barter_storage"
     * Ensures the directory exists on construction.
     */
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

    /** Extract the key from an object (used for save/delete pathing). */
    public abstract K getKey(T type);

    /** Resolve the file path for a key under the parent directory. */
    public abstract Path getChild(K key, Path parent);

    /** Deserialize an object from a ready-to-read Reader. */
    public abstract T loadFromFile(Reader fileReader) throws Exception;

    /**
     * Load an object by key. Returns null if file absent.
     * Implementations of loadFromFile(...) are responsible for parsing.
     */
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

    /**
     * Delete the file corresponding to the given object (uses its key).
     * Returns false if the file did not exist.
     */
    @Override
    public boolean delete(@NotNull T type) throws Exception {
        Path file = this.getChild(this.getKey(type), this.parent);
        if (Files.notExists(file)) {
            return false;
        }

        Files.delete(file);
        return true;
    }

    /** Serialize and write an object to its file (create if absent). */
    public abstract boolean saveToFile(T object, Writer writer) throws Exception;

    /**
     * Save the object to disk (overwrites existing content).
     * Creates the file if it doesn't exist.
     */
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

    /**
     * Load all objects by iterating files in the parent directory.
     * Any parsing error for a single file propagates as a RuntimeException.
     */
    @Override
    public List<T> getAll() throws Exception {
        try (var paths = Files.list(this.parent)) {
            return paths.map(file -> {
                try (BufferedReader reader = Files.newBufferedReader(file)) {
                    return this.loadFromFile(reader);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).toList();
        }
    }
}
