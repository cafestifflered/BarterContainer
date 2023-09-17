package com.stifflered.bartercontainer.util;

import java.io.Closeable;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * This class stores objects in a hash map, and discards them if they remain unused
 * for a certain period of time. Once discarded, objects will be moved to a weakly
 * referenced hash map and remain there until garbage collected. If an expired object
 * is accessed before it is garbage collected, it will be moved back to the strongly
 * referenced hash map.
 * <p>
 * All methods in this class are thread safe.
 */
public class WeakStorage<K, V> implements Closeable {

    private final long dataTimeout;
    /**
     * The amount of time between data timeout checks, in milliseconds.
     */
    private final long discardDelay;

    private final Thread timeoutThread;
    private final Map<K, Entry> storage = new HashMap<>();
    private final Map<K, WeakReference<V>> weakStorage = new HashMap<>();
    private boolean closed = false;

    @SuppressWarnings("BusyWait")
    public WeakStorage(long dataTimeout) {
        this.dataTimeout = dataTimeout;
        this.discardDelay = dataTimeout * 3;

        this.timeoutThread = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(this.discardDelay);
                    this.processTimeout();
                    this.weakStorage.entrySet().removeIf((entry) -> entry.getValue().get() == null);
                }
            } catch (InterruptedException ignored) {
            }
        });
        this.timeoutThread.start();
        this.timeoutThread.setName("WeakStorage Check Thread");
        this.timeoutThread.setPriority(2);
    }

    private void processTimeout() {
        long currentTime = System.currentTimeMillis();

        Iterator<Map.Entry<K, Entry>> iterator = this.storage.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<K, Entry> entry = iterator.next();
            Entry dataEntry = entry.getValue();
            long timeElapsed = currentTime - dataEntry.loadedTime;

            if (timeElapsed > this.dataTimeout) {
                iterator.remove();
                this.weakStorage.put(entry.getKey(), new WeakReference<>(dataEntry.data));
            }
        }
    }

    /**
     * If your object is not found, it was most likely garbage collected.
     */
    public synchronized V get(K key) {
        if (this.closed) {
            throw new IllegalStateException();
        }

        Entry storedObject = this.storage.get(key);
        if (storedObject != null) {
            storedObject.loadedTime = System.currentTimeMillis();
            return storedObject.data;
        }

        WeakReference<V> reference = this.weakStorage.get(key);
        if (reference != null) {
            V weakObject = reference.get();
            if (weakObject != null) {
                // See comment in the put method
                this.put(key, weakObject);
                return weakObject;
            }
        }

        return null;
    }

    public V getOrDefault(K key, V value) {
        V stored = this.get(key);

        if (stored == null) {
            return value;
        }

        return stored;
    }

    public boolean isStored(K key) {
        return this.get(key) != null;
    }

    /**
     * Does not push the object back to the main map.
     */
    public synchronized boolean isWeaklyStored(K key) {
        return this.weakStorage.get(key).get() != null;
    }

    public void put(K key, V object) {
        if (this.closed) {
            throw new IllegalStateException();
        }

        Entry entry = new Entry(object, System.currentTimeMillis());

        synchronized (this) {
            // It is possible for the object to already be present in
            // the weak hash map, but that should not have any effect
            // on functionality
            this.storage.put(key, entry);
        }
    }

    public synchronized V remove(K key) {
        Entry entry = this.storage.remove(key);
        WeakReference<V> reference = this.weakStorage.remove(key);

        if (entry != null) {
            return entry.data;
        } else if (reference != null) {
            return reference.get();
        }

        return null;
    }

    @Override
    public synchronized void close() {
        this.closed = true;
        this.storage.clear();
        this.weakStorage.clear();
        this.timeoutThread.interrupt();
    }

    public Collection<V> values() {
        return this.storage.values().stream().map((entry) -> entry.data).toList();
    }

    @Override
    public String toString() {
        return "WeakStorage{" +
                "dataTimeout=" + this.dataTimeout +
                ", discardDelay=" + this.discardDelay +
                ", timeoutThread=" + this.timeoutThread +
                ", closed=" + this.closed +
                ", storage=" + this.storage +
                ", weakStorage=" + this.weakStorage +
                '}';
    }

    private class Entry {

        private final V data;
        private long loadedTime;

        public Entry(V data, long loadedTime) {
            this.data = data;
            this.loadedTime = loadedTime;
        }

        @Override
        public String toString() {
            return "Entry{" +
                    "data=" + this.data +
                    ", loadedTime=" + this.loadedTime +
                    '}';
        }
    }
}
