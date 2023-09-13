package com.stifflered.tcchallenge.util.storage.chunked;

import com.stifflered.tcchallenge.tree.root.type.Root;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class SimpleChunkedStorage<T> {

    private final Long2ObjectOpenHashMap<DataChunk<T>> chunkMap = new Long2ObjectOpenHashMap<>();

    public static long asLong(int x, int z) {
        return (long) x & 4294967295L | ((long) z & 4294967295L) << 32;
    }

    public static int getX(long key) {
        return (int) (key & 4294967295L);
    }

    public static int getZ(long key) {
        return (int) (key >>> 32 & 4294967295L);
    }

    public void addChunk(int x, int z, @NotNull DataChunk<T> rootChunk) {
        Objects.requireNonNull(rootChunk);

        this.chunkMap.put(asLong(x, z), rootChunk);
    }

    @Nullable
    public DataChunk<T> getChunk(int x, int z) {
        return this.chunkMap.get(asLong(x, z));
    }

    @Nullable
    public DataChunk<T> deleteChunk(int x, int z) {
        return this.chunkMap.remove(asLong(x, z));
    }

    public boolean hasChunk(int x, int z) {
        return this.chunkMap.containsKey(asLong(x, z));
    }

    public void clear() {
        this.chunkMap.clear();
    }

    public Long2ObjectOpenHashMap.FastEntrySet<DataChunk<T>> chunkEntries() {
        return this.chunkMap.long2ObjectEntrySet();
    }

    // Utility

    @Nullable
    public T getData(Location location) {
        return this.getData(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Nullable
    public T getData(int x, int y, int z) {
        DataChunk<T> chunk = this.getChunk(x >> 4, z >> 4);
        if (chunk != null) {
            return chunk.getData(x, y, z);
        }

        return null;
    }

    public void putData(int xCoord, int yCoord, int zCoord, T data) {
        int x = xCoord >> 4;
        int z = zCoord >> 4;
        DataChunk<T> chunk = this.getChunk(x, z);
        if (chunk == null) {
            chunk = new DataChunk<>(x, z);
            this.addChunk(x, z, chunk);
        }

        chunk.addData(xCoord, yCoord, zCoord, data);
    }

    public void putData(Location location, T data) {
        this.putData(location.getBlockX(), location.getBlockY(), location.getBlockZ(), data);
    }

    public void removeData(Location location) {
        int x = location.getBlockX() >> 4;
        int z = location.getBlockZ() >> 4;
        DataChunk<T> chunk = this.getChunk(x, z);
        if (chunk == null) {
            return;
        }

        chunk.removeData(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
