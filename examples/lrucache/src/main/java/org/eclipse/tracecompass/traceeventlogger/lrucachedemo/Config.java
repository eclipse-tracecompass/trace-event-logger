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

/**
 * Config class that parses CLI arguments and provides the 
 * parameters to run the simulation
 */
public class Config {
    /**
     * Number of elements the cache can contain
     */
    public int cacheSize = 100;
    /**
     * Number of elements in the data to be cached
     */
    public int dataSize = 100;
    /**
     * The delay in ms to be used when a data element is 
     * "loaded", to simulate it being accesses from a slower
     * source, e.g. disk, network, or carrier pigeon
     */
    public int cacheMissDelayMs = 5;
    /**
     * Number of reader threads, that each will be 
     * reading the data through the cache
     */
    public int numThreads = 20;
    /**
     * The delay between reader threads start up
     */
    public int readersStartupDelayMs = 50;
    /**
     * Whether or not to write to trace log, the cache warm-up phase,
     * where each element of the data is read once to populate the 
     * cache
     */
    public boolean logWarmup = false;
    /**
     * Whether or not to write detailed information on STDOUT
     */
    public boolean verbose = false;

    private static Config instance;
    
    /**
     * @return an instance of the static config object
     */
    public static Config getInstance() {
        return instance;
    }

    /**
     * @param args CLI arguments the simulator was called-with
     * @return a static instance of the Config object that contains the
     *         simulation's parameters for this run, that reflects the 
     *         CLI arguments provided, and when not, the default values
     *         to be used.
     */
    @SuppressWarnings("nls")
    public static Config fromArgs(String[] args) {
        Config config = new Config();
        for (String arg : args) {
            if (arg.startsWith("--cache-size=")) {
                config.cacheSize = Integer.parseInt(arg.split("=")[1]);
            } else if (arg.startsWith("--data-size=")) {
                config.dataSize = Integer.parseInt(arg.split("=")[1]);
            } else if (arg.startsWith("--num-threads=")) {
                config.numThreads = Integer.parseInt(arg.split("=")[1]);
            } else if (arg.startsWith("--cache-miss-delay-ms=")) {
                config.cacheMissDelayMs = Integer.parseInt(arg.split("=")[1]);
            } else if (arg.startsWith("--readers-startup-delay-ms=")) {
                config.readersStartupDelayMs = Integer.parseInt(arg.split("=")[1]);
            } else if (arg.startsWith("--log-warmup=")) {
                config.logWarmup = Boolean.parseBoolean(arg.split("=")[1]);
            } else if (arg.startsWith("--verbose=")) {
                config.verbose = Boolean.parseBoolean(arg.split("=")[1]);
            }
        }
        instance = config;
        return instance;
    }

    /**
     * Prints the current configuration
     */
    @SuppressWarnings("nls")
    public static void printConfig() {
        System.out.println("\n--- Run Configuration ---");
        System.out.printf("cacheSize             : %d%n", instance.cacheSize);
        System.out.printf("dataSize              : %d%n", instance.dataSize);
        System.out.printf("threads               : %d%n", instance.numThreads);
        System.out.printf("cacheMissDelay        : %d ms%n", instance.cacheMissDelayMs);
        System.out.printf("readersStartupDelay-ms: %d ms%n", instance.readersStartupDelayMs);
        System.out.printf("log-warmup            : %b%n", instance.logWarmup);
        System.out.printf("verbose               : %b%n", instance.verbose);
    }
}