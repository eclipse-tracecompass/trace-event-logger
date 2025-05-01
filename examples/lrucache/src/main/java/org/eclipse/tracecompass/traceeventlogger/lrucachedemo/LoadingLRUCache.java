/*******************************************************************************
 * Copyright (c) 2025 Ericsson
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the “Software”), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 *
 * SPDX-License-Identifier: MIT
 *******************************************************************************/
package org.eclipse.tracecompass.traceeventlogger.lrucachedemo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.tracecompass.traceeventlogger.LogUtils;

/**
 * LoadingLRUCache - a toy loading cache with LRU (least recently used) eviction
 * 
 * @param <K> K the type of the cache key
 * @param <V> V the type of the cache value
 */
public class LoadingLRUCache<K, V> {
    private static Logger LOGGER = Logger.getAnonymousLogger();
    private static boolean verbose = false;

    /**
     * @param verbose set verbose mode - more printouts to STDOUT if enabled
     */
    public static void setVerbose(boolean verbose) {
        LoadingLRUCache.verbose = verbose;
    }

    private final int capacity;
    private final Function<K, V> loader;

    // the cache
    private final Map<K, V> cache = new ConcurrentHashMap<>();
    // granular lock to control access to each cache element
    // note: we could rely on the ConcurrentHashMap locking, when
    // updating the cache, but providing our own locking permits 
    // adding tracepoints to it :)
    private final Map<K, Object> locks = new ConcurrentHashMap<>();
    // LRU tracking: keeps track of cache element access order
    private final Map<K, Boolean> lruTracker = new LinkedHashMap<>(16, 0.75f, true);

    // cache statistics
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicInteger hitsAfterWait = new AtomicInteger();
    private final AtomicInteger misses = new AtomicInteger();
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger evictions = new AtomicInteger();
    private final AtomicLong totalTimeNanos = new AtomicLong();

    /**
     * @param capacity max number of elements the cache can hold
     * @param loader call-back function the cache will use to fetch elements,
     *               is provided by the cache user code
     */
    public LoadingLRUCache(int capacity, Function<K, V> loader) {
        this.capacity = capacity;
        this.loader = loader;
    }

    /**
     * Get an element from the cache that corresponds to a certain key. If that
     * element is not in the cache, the loader function provided in the constructor
     * will be used to load it in the cache.
     * 
     * @param key key of the element
     * @return value of the element corresponding to key
     */
    @SuppressWarnings("nls")
    public V get(K key) {
        long start = System.nanoTime();
        requests.incrementAndGet();
        V value = cache.get(key);
        if (value != null) {
            try (LogUtils.ScopeLog sl2 = new LogUtils.ScopeLog(LOGGER, Level.FINE,
                    "CACHE:HIT!", "element", key.toString())) {
                hits.incrementAndGet();
                updateLRU(key);
                log("HIT  - key: " + key);
                totalTimeNanos.addAndGet(System.nanoTime() - start);
            }
            return value;
        }

        try (LogUtils.ScopeLog sl3 = new LogUtils.ScopeLog(LOGGER, Level.FINE,
                "CACHE:GET()-SYNCHRONIZED-BLOCK", "element", key.toString())) {
            // Per-key locking
            synchronized (locks.computeIfAbsent(key, k -> new Object())) {
                // Double-check after acquiring lock
                value = cache.get(key);
                if (value != null) {
                    try (LogUtils.ScopeLog sl4 = new LogUtils.ScopeLog(LOGGER, Level.FINE,
                            "CACHE:HIT_AFTER_WAIT", "element", key.toString())) {
                        hits.incrementAndGet();
                        hitsAfterWait.incrementAndGet();
                        log("HIT  - key: " + key.toString());
                        totalTimeNanos.addAndGet(System.nanoTime() - start);
                    }
                    updateLRU(key);
                    return value;
                }
                try (LogUtils.ScopeLog sl5 = new LogUtils.ScopeLog(LOGGER, Level.FINE,
                        "CACHE:MISS!", "element", key.toString())) {
                    // cache miss
                    misses.incrementAndGet();
                    log("MISS - key: " + key.toString());
                    // simulate loading value from slower source
                    value = loader.apply(key);
                    log("Loaded value from slow storage: " + value.toString());
                    // add value to the cache, evicting old entries if necessary
                    try (LogUtils.ScopeLog sl6 = new LogUtils.ScopeLog(LOGGER, Level.FINE,
                        "CACHE:INSERTION", "element", key.toString())) {
                        insert(key, value);
                    }
                }
            }
        }
        totalTimeNanos.addAndGet(System.nanoTime() - start);
        return value;
    }

    /**
     * add key/value pair to the cache, evicting old entries if necessary
     */
    @SuppressWarnings("nls")
    private void insert(K key, V value) {
        synchronized (lruTracker) {
            evictLRU();
            // for comparison, an alternative eviction algorithm
            // evictRandom(); 

            cache.put(key, value);
            updateLRU(key);
        }
    }

    /**
     * Evict to make space for a new cache entry, if needed.
     * Use the LRU strategy.
     */
    @SuppressWarnings("nls")
    private void evictLRU() {
        Iterator<K> it = lruTracker.keySet().iterator();
        K eldest;
        while (lruTracker.size() >= capacity) {
            eldest = it.next();
            try (LogUtils.ScopeLog sl = new LogUtils.ScopeLog(LOGGER, Level.FINE,
                    "CACHE:EVICTION! (LRU)", "element", eldest.toString())) {
                it.remove();
                cache.remove(eldest);
                log("EVICTION - key: " + eldest.toString());
                evictions.incrementAndGet();
            }
        }
    }

    /**
     * Alternate version of "evictLRU()". 
     * Evict to make space for a new cache entry, if needed.
     * Pick a cache entry at random to evict. 
     */
    @SuppressWarnings("nls")
    private void evictRandom() {
        while (lruTracker.size() >= capacity) {
            int randomElem = (int) (Math.random() * capacity);
            
            try (LogUtils.ScopeLog sl = new LogUtils.ScopeLog(LOGGER, Level.FINE,
                    "CACHE:EVICTION! {Random}", "element", randomElem)) {
                lruTracker.remove(randomElem);
                cache.remove(randomElem);
                log("EVICTION - key: " + randomElem);
            }
        }
    }

    /**
     * Insert/re-insert key in LRU map so it takes its place as the latest accessed
     */
    private void updateLRU(K key) {
        synchronized (lruTracker) {
            lruTracker.put(key, Boolean.TRUE);
        }
    }

    @SuppressWarnings("nls")
    private void log(String msg) {
        if (verbose) {
            System.out.printf("[%s] %s%n", Thread.currentThread().getName(), msg);
        }
    }

    /**
     * Resets the cache's stats counters
     */
    public void resetStats() {
        hits.set(0);
        misses.set(0);
        requests.set(0);
        evictions.set(0);
        totalTimeNanos.set(0);
    }

    /**
     * Prints the cache's stats counters
     */
    @SuppressWarnings("nls")
    public void printStats() {
        int h = hits.get();
        int hAfterW = hitsAfterWait.get();
        int adjustedHits = h - hAfterW;
        int m = misses.get();
        int r = requests.get();
        int e = evictions.get();
        double avgTimeMs = totalTimeNanos.get() / 1_000_000.0 / r;
        double tt = totalTimeNanos.get() / 1_000_000;

        System.out.println("\n--- Cache Stats ---");
        System.out.printf("Total Requests         : %d%n", r);
        System.out.printf("Cache Hits (total)     : %d (%.2f%%)%n", h, 100.0 * h / r);
        System.out.printf("Cache Misses           : %d (%.2f%%)%n", m, 100.0 * m / r);
        System.out.printf("Cache Evictions        : %d (%.2f%%)%n", e, 100.0 * e / r);
        System.out.printf("Avg get() Time/Op      : %.3f ms%n", avgTimeMs);
        System.out.printf("Total get() Time       : %d ms\n", totalTimeNanos.get() / 1_000_000);
        System.out.println("\n--- Counting any cache-hit-after-wait as a miss ---");
        System.out.printf("Cache Hits (after wait): %d / %d%n", hAfterW, h);
        System.out.printf("Cache Hits (adjusted)  : %d (%.2f%%)%n", adjustedHits, 100.0 * adjustedHits / r);
        System.out.printf("Cache Misses (adjusted): %d (%.2f%%)%n", m + hAfterW, 100.0 * (m + hAfterW) / r);
        System.out.println("-------------------\n");

        try (LogUtils.ScopeLog sl = new LogUtils.ScopeLog(LOGGER, Level.FINE,
                "--- Cache Stats --- : see 'args'",
                "Total Requests", r,
                "Cache Hits (total)", h,
                "Cache Misses", m,
                "Cache Evictions", e,
                "Avg get() Time/Op(ms)", avgTimeMs,
                "Total get() Time(ms)", tt)) {
            // do nothing
        }
        try (LogUtils.ScopeLog sl = new LogUtils.ScopeLog(LOGGER, Level.FINE,
                "--- Adjusted Cache Stats, counting any cache-hit-after-wait as a miss --- : see 'args'",
                "Total Requests", r,
                "Cache Hits (adjusted)", adjustedHits,
                "Cache Misses (adjusted)", m + hAfterW)) {
            // do nothing
        }
    }

}
