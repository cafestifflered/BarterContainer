package com.stifflered.tcchallenge.util.storage.chunked;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.bukkit.Location;

public class DataChunk<T> {

    private final Long2ObjectOpenHashMap<T> data;

    private final int minX;
    private final int minZ;

    private final ChunkPos pos;

    private boolean dirty = false;

    public DataChunk(ChunkPos rootChunkPos) {
        this(rootChunkPos, new Long2ObjectOpenHashMap<>());
    }

    public DataChunk(ChunkPos rootChunkPos, Long2ObjectOpenHashMap<T> data) {
        this.pos = rootChunkPos;
        this.minX = rootChunkPos.x() << 4;
        this.minZ = rootChunkPos.z() << 4;
        this.data = data;
    }

    public DataChunk(int chunkX, int chunkZ) {
        this(new ChunkPos(chunkX, chunkZ), new Long2ObjectOpenHashMap<>());
    }

    public T getData(int x, int y, int z) {
        int relativeX = this.relativeX(x);
        int relativeY = this.relativeY(y);
        int relativeZ = this.relativeZ(z);

        return this.data.get(this.packRelativeLocation(relativeX, relativeY, relativeZ));
    }

    public T getData(Location location) {
        return this.getData(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public void addData(int x, int y, int z, T data) {
        int relativeX = this.relativeX(x);
        int relativeY = this.relativeY(y);
        int relativeZ = this.relativeZ(z);

        this.data.put(this.packRelativeLocation(relativeX, relativeY, relativeZ), data);
        this.dirty = true;
    }

    public void addData(Location location, T data) {
        this.addData(location.getBlockX(), location.getBlockY(), location.getBlockZ(), data);
    }

    public void removeData(int x, int y, int z) {
        int relativeX = this.relativeX(x);
        int relativeY = this.relativeY(y);
        int relativeZ = this.relativeZ(z);

        this.data.remove(this.packRelativeLocation(relativeX, relativeY, relativeZ));
        this.dirty = true;
    }

    private int relativeX(int x) {
        return x - this.minX;
    }

    private int relativeY(int y) {
        return y;
    }

    private int relativeZ(int z) {
        return z - this.minZ;
    }

    private long packRelativeLocation(int xCoord, int yCoord, int zCoord) {
        byte x = (byte) xCoord;
        int y = yCoord;
        byte z = (byte) zCoord;

        return ((long) x & 0xff)
                | ((long) z & 0xff) << Byte.SIZE
                | ((long) y) << Byte.SIZE * 2;
    }


    public ChunkPos getPos() {
        return this.pos;
    }

    public Long2ObjectOpenHashMap<T> getData() {
        return this.data;
    }

    // Utility
    public Location toLocation(long key) {
        int x = (byte) key;
        int y = (int) (key >> Byte.SIZE * 2);
        int z = (byte) (key >> Byte.SIZE);

        return new Location(
                null,
                x,
                y,
                z
        ).add(this.minX, 0, this.minZ);
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
}
