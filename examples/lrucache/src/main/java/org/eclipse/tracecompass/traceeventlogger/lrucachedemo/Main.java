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

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.tracecompass.traceeventlogger.AsyncFileHandler;
import org.eclipse.tracecompass.traceeventlogger.LogUtils;

/**
 * Driver for the LoadingLRUCache simulation
 */
public class Main {
    static Config config;
    static String[] cacheableData;
    static LoadingLRUCache<Integer, String> cache;
    private static Logger LOGGER = Logger.getAnonymousLogger();
    private static AsyncFileHandler aHandler = null;

    private final static AtomicInteger numParallelLoads = new AtomicInteger();

    /**
     * Main class
     * 
     * @param args CLI arguments - see README
     */
    @SuppressWarnings("nls")
    public static void main(String[] args) {
        config = Config.fromArgs(args);
        initializeLogging(true);
        try (LogUtils.ScopeLog sl = new LogUtils.ScopeLog(LOGGER, Level.FINE,
                "--- Configuration ---: see 'args'",
                "cacheSize", config.cacheSize,
                "dataSize", config.dataSize,
                "numThreads", config.numThreads,
                "cacheMissDelayMs", config.cacheMissDelayMs,
                "readersStartupDelay", config.readersStartupDelayMs,
                "logWarmup", config.logWarmup)) {
            Config.printConfig();        

            // Initialize backing data
            cacheableData = new String[config.dataSize];
            for (int i = 0; i < config.dataSize; i++) {
                cacheableData[i] = "ELEMENT " + i;
            }

            // Create cache with loading logic and simulated delay
            cache = new LoadingLRUCache<Integer, String>(
                    config.cacheSize,
                    key -> {
                        numParallelLoads.incrementAndGet();
                        try (LogUtils.ScopeLog sl2 = new LogUtils.ScopeLog(LOGGER, Level.FINE,
                                "LOAD-FROM-SLOW-STORAGE", "element", key)) {
                            simulateLoadDelay();
                        }
                        numParallelLoads.decrementAndGet();
                        return cacheableData[key];
                    });
            cache.setVerbose(config.verbose);

            // Warm-up thread: populate the cache before starting the readers
            enableLogging(config.logWarmup);
            try (LogUtils.ScopeLog sl3 = new LogUtils.ScopeLog(LOGGER, Level.FINE, "WARMUP")) {
                Thread warmUpThread = new Thread(new SequentialReader(0), "Warm-up Thread");
                warmUpThread.start();

                try {
                    // Wait for the warm-up to complete
                    warmUpThread.join();
                    if (config.verbose) {
                        System.out.println("Cache warmed up.");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // small delay between warm-up and reader threads
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            

            // warm-up not part of cache stats
            cache.resetStats();
            enableLogging(true);

            // Start reader threads
            List<Thread> readers = new ArrayList<>();
            for (int i = 1; i <= config.numThreads; i++) {
                String threadName = String.format("Reader-%03d", i);
                Thread t = new Thread(new SequentialReader(i), threadName);
                readers.add(t);
                t.start();
                simulateDelayBetweenReaders();
            }

            // Wait for all threads to finish
            for (Thread t : readers) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            cache.printStats();
            enableLogging(false);
        }
        System.exit(0);
    }

    private static void simulateDelayBetweenReaders() {
        try {
            Thread.sleep(config.readersStartupDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Reader thread, that exercises the cache. This reader accesses 
     * the data structure sequentially, through the cache - i.e. always
     * from the first to the last element. This may seem weird, and it 
     * has big implications regarding the cache configuration, but it 
     * does correspond to a real-life case we encountered, and want to
     * simulate here.
     */
    static class SequentialReader implements Runnable {
        private final int id;

        public SequentialReader(int id) {
            this.id = id;
        }

        @SuppressWarnings("nls")
        @Override
        public void run() {
            try (LogUtils.ScopeLog sl = new LogUtils.ScopeLog(LOGGER, Level.FINE,
                    Thread.currentThread().getName())) {
                for (int i = 0; i < config.dataSize; i++) {
                    @SuppressWarnings("nls")
                    String iter = String.format("ITERATION-%04d", i);
                    try (LogUtils.ScopeLog sl2 = new LogUtils.ScopeLog(LOGGER,
                            Level.FINE, iter)) {
                        String value = cache.get(i);
                        // simulate some processing
                        doSomeWork(value);
                    }
                }
                if (config.verbose) {
                    System.out.printf("Reader thread %d is done\n", this.id);
                }
            }
        }

        /**
         * Simulate doing some with the data from he cache
         * 
         * @param value
         */
        private void doSomeWork(String value) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void simulateLoadDelay() {
        try {
            Thread.sleep(config.cacheMissDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Alternate version of simulateLoadDelay(), that adds an 
     * exponential factor to the load delay, based on the number
     * of concurrent load operations running concurrently. This
     * simulates the load operation being costly in resources,
     * such that running multiples in parallel results in making
     * them take more time.
     * 
     * The exponential factor used is exaggerated on purpose, to 
     * obtain interesting traces more easily.
     */
    @SuppressWarnings("nls")
    private static void simulateLoadDelayExponential() {
        int parallelLoads = numParallelLoads.get();
        double exponentialFactor = 0.5;
        double baseDelay = (double)config.cacheMissDelayMs;
        double exponent = 1.0 + (exponentialFactor * (parallelLoads - 1.0));

        double delay = Math.pow(baseDelay, exponent);
        System.out.println("simulateLoadDelayExponential() - parallelLoads: " + 
            parallelLoads +", exponent: " + exponent + ", delay: " + delay);
        try {
            Thread.sleep((int)delay);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("nls")
    private static void initializeLogging(boolean enable) {
        boolean found = false;
        for (Handler handler : Logger.getLogger("").getHandlers()) {
            if (handler instanceof AsyncFileHandler) {
                if (config.verbose) {
                    System.out.println("AsyncFileHandler found: " + handler.toString());
                }
                aHandler = (AsyncFileHandler) handler;
                found = true;
            }
        }
        if (!found) {
            System.out.println("Error: Could not find AsyncFileHandler - Exiting");
            System.exit(1);
        }
        enableLogging(enable);
    }

    /**
     * @param e boolean : whether to enable logging or not
     */
    public static void enableLogging(boolean e) {
        aHandler.setEnabled(e);
    }

}
