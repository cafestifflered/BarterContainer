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
/* Additional notes:
   - "Strong" map = prevents GC while the value lives here.
   - After dataTimeout ms without access, entries are "demoted" to the weak map.
   - Weak map uses WeakReference, so entries can disappear at any time if GC runs.
   - If you access a still-alive weak entry via get(), it is "promoted" back to strong storage.
   - A background thread periodically:
       • moves expired entries to weakStorage (processTimeout)
       • prunes weakStorage entries whose referents were GC'd
*/
public class WeakStorage<K, V> implements Closeable {

    private final long dataTimeout;

    /**
     * The amount of time between data timeout checks, in milliseconds.
     */
    private final long discardDelay;

    private final Thread timeoutThread;

    // Strongly-referenced storage (resets "last used" timestamp on access).
    private final Map<K, Entry> storage = new HashMap<>();

    // Weakly-referenced storage (values may be GC'd at any time).
    private final Map<K, WeakReference<V>> weakStorage = new HashMap<>();

    private boolean closed = false;

    @SuppressWarnings("BusyWait")
    public WeakStorage(long dataTimeout) {
        this.dataTimeout = dataTimeout;
        this.discardDelay = dataTimeout * 3; // check less frequently than the timeout itself

        // Periodic maintenance thread:
        // 1) demote expired entries to weakStorage
        // 2) prune dead weak references
        this.timeoutThread = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(this.discardDelay);
                    this.processTimeout();
                    // Remove entries whose WeakReference referent has been GC'd
                    this.weakStorage.entrySet().removeIf((entry) -> entry.getValue().get() == null);
                }
            } catch (InterruptedException ignored) {
                // Interrupted on close(); exit loop gracefully
            }
        });
        this.timeoutThread.start();
        this.timeoutThread.setName("WeakStorage Check Thread");
        this.timeoutThread.setPriority(2);
    }

    // Move expired entries from strong map to weak map based on last-access time.
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
            // Pulled from messages.yml so server owners can theme/translate.
            throw new IllegalStateException(Messages.fmt("util.weak_storage.closed"));
        }

        // 1) Try strong storage first; update last-access time if found.
        Entry storedObject = this.storage.get(key);
        if (storedObject != null) {
            storedObject.loadedTime = System.currentTimeMillis(); // refresh TTL
            return storedObject.data;
        }

        // 2) Then try weak storage; if still alive, promote back to strong storage.
        WeakReference<V> reference = this.weakStorage.get(key);
        if (reference != null) {
            V weakObject = reference.get();
            if (weakObject != null) {
                // Promotion: make it strong again so it won't be GC'd while in active use.
                this.put(key, weakObject);
                return weakObject;
            }
        }

        // 3) Not found (either never stored or already GC'd).
        return null;
    }

    // Convenience: get-or-default without changing storage state.
    public V getOrDefault(K key, V value) {
        V stored = this.get(key);

        if (stored == null) {
            return value;
        }

        return stored;
    }

    // Returns true if get(key) finds a value (either strong or still-alive weak, which would promote).
    public boolean isStored(K key) {
        return this.get(key) != null;
    }

    /**
     * Does not push the object back to the main map.
     */
    public synchronized boolean isWeaklyStored(K key) {
        // NB: This can NPE if key is absent; usage should ensure key exists in weakStorage.
        // If not present, get(key) would be safer but would also promote.
        return this.weakStorage.get(key).get() != null;
    }

    // Store (or promote) a value into strong storage and reset its TTL.
    public void put(K key, V object) {
        if (this.closed) {
            throw new IllegalStateException(Messages.fmt("util.weak_storage.closed"));
        }

        Entry entry = new Entry(object, System.currentTimeMillis());

        synchronized (this) {
            // If present in weakStorage, we intentionally leave it there as well.
            // Strong storage is authoritative for access; weak entry will be pruned on the next sweep if needed.
            this.storage.put(key, entry);
        }
    }

    // Removes an entry from both strong and weak maps; returns whatever value still exists.
    public synchronized V remove(K key) {
        Entry entry = this.storage.remove(key);
        WeakReference<V> reference = this.weakStorage.remove(key);

        if (entry != null) {
            return entry.data;
        } else if (reference != null) {
            return reference.get(); // may be null if already GC'd
        }

        return null;
    }

    @Override
    public synchronized void close() {
        // Prevent further use, clear maps, and stop the maintenance thread.
        this.closed = true;
        this.storage.clear();
        this.weakStorage.clear();
        this.timeoutThread.interrupt();
    }

    // Snapshot of the currently strong-stored values (weak-stored values are not included).
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

    // Internal wrapper to track value + last-access timestamp for TTL checks.
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
