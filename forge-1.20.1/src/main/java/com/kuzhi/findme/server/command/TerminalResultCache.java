package com.kuzhi.findme.server.command;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Bounded server-tick cache used to replay terminal protocol results idempotently. */
final class TerminalResultCache<K, T> {
    private final Map<K, Entry<T>> entries = new HashMap<>();

    T get(K key) {
        Entry<T> entry = key == null ? null : entries.get(key);
        return entry == null ? null : entry.value();
    }

    boolean contains(K key) {
        return key != null && entries.containsKey(key);
    }

    void put(K key, T value, long expiresAt) {
        if (key != null && value != null) {
            entries.put(key, new Entry<>(value, expiresAt));
        }
    }

    int expire(long now) {
        int removed = 0;
        Iterator<Entry<T>> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            if (now >= iterator.next().expiresAt()) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    void clear() {
        entries.clear();
    }

    private record Entry<T>(T value, long expiresAt) {
    }
}
