/*******************************************************************************
 * Copyright (c) 2024, 2025 Ericsson
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

package org.eclipse.tracecompass.traceeventlogger;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Test;

/**
 * Test the performance
 */
public class TestLoggerBenchmark {

    private Logger fLogger;
    private Handler oldFileHandler;
    private Handler newFileHandler;
    private File[] files = new File[2];
    private long warmUp = 2000;
    private long maxRuns = warmUp * 100;
    private double growth = 2.3;
    private final static float newAsyncPerformenceThreshold = 2.5f;

    /**
     * Benchmark events with fields
     *
     * @throws SecurityException
     *             won't happen
     * @throws IOException
     *             won't happen
     */
    @Test
    public void testBench() throws SecurityException, IOException {
        fLogger = Logger.getAnonymousLogger();
        files[0] = File.createTempFile("trace-old", ".json"); //$NON-NLS-1$ //$NON-NLS-2$
        files[0].deleteOnExit();
        files[1] = File.createTempFile("trace-new", ".json"); //$NON-NLS-1$ //$NON-NLS-2$
        files[1].deleteOnExit();
        oldFileHandler = new FileHandler(files[0].getAbsolutePath());
        oldFileHandler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                return record.getMessage() + ",\n"; //$NON-NLS-1$
            }
        });
        oldFileHandler.setLevel(Level.ALL);
        fLogger.addHandler(oldFileHandler);
        fLogger.setLevel(Level.ALL);
        Logger logger = fLogger;
        List<Float> asyncNewVsSyncOld = new ArrayList<>();
        List<Long> asyncNew = new ArrayList<>();
        List<Long> asyncOld = new ArrayList<>();
        List<Long> syncNew = new ArrayList<>();
        List<Long> syncOld = new ArrayList<>();
        List<Long> run = new ArrayList<>();
        for (long runs = warmUp; runs < maxRuns; runs *= growth) {
            for (long i = 0; i < warmUp; i++) {
                try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.FINE, "foo")) { //$NON-NLS-1$
                    try (LogUtils.ScopeLog log1 = new LogUtils.ScopeLog(logger, Level.FINER, "bar")) { //$NON-NLS-1$
                        try (LogUtils.ScopeLog log2 = new LogUtils.ScopeLog(logger, Level.FINEST, "baz")) { //$NON-NLS-1$
                            // do something
                            new Object();
                        }
                    }
                }
            }

            long start = System.nanoTime();
            for (long i = 0; i < runs; i++) {
                try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.FINE, "foo", "run", runs, "test", i)) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    try (LogUtils.ScopeLog log1 = new LogUtils.ScopeLog(logger, Level.FINER, "bar", "run", runs, "test", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                            i)) {
                        try (LogUtils.ScopeLog log2 = new LogUtils.ScopeLog(logger, Level.FINEST, "baz", "run", runs, //$NON-NLS-1$ //$NON-NLS-2$
                                "test", i)) { //$NON-NLS-1$
                            // do something
                            new Object();
                        }
                    }
                }
            }
            long end = System.nanoTime();
            syncNew.add(end - start);
            for (long i = 0; i < warmUp; i++) {
                try (OldLogUtils.ScopeLog log = new OldLogUtils.ScopeLog(logger, Level.FINE, "foo", "run", runs, "test", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        i)) {
                    try (OldLogUtils.ScopeLog log1 = new OldLogUtils.ScopeLog(logger, Level.FINER, "bar", "run", runs, //$NON-NLS-1$ //$NON-NLS-2$
                            "test", i)) { //$NON-NLS-1$
                        try (OldLogUtils.ScopeLog log2 = new OldLogUtils.ScopeLog(logger, Level.FINEST, "baz", "run", //$NON-NLS-1$ //$NON-NLS-2$
                                runs, "test", i)) { //$NON-NLS-1$
                            // do something
                            new Object();
                        }
                    }
                }
            }

            long start2 = System.nanoTime();
            for (long i = 0; i < runs; i++) {
                try (OldLogUtils.ScopeLog log = new OldLogUtils.ScopeLog(logger, Level.FINE, "foo", "run", runs, "test", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        i)) {
                    try (OldLogUtils.ScopeLog log1 = new OldLogUtils.ScopeLog(logger, Level.FINER, "bar", "run", runs, //$NON-NLS-1$ //$NON-NLS-2$
                            "test", i)) { //$NON-NLS-1$
                        try (OldLogUtils.ScopeLog log2 = new OldLogUtils.ScopeLog(logger, Level.FINEST, "baz", "run", //$NON-NLS-1$ //$NON-NLS-2$
                                runs, "test", i)) { //$NON-NLS-1$
                            // do something
                            new Object();
                        }
                    }
                }
            }
            long end2 = System.nanoTime();
            syncOld.add(end2 - start2);
            run.add(runs);
        }
        fLogger.removeHandler(oldFileHandler);
        newFileHandler = makeAsyncFileHandler(files[1].getAbsolutePath());
        fLogger.addHandler(newFileHandler);
        for (long runs = warmUp; runs < maxRuns; runs *= growth) {
            for (long i = 0; i < warmUp; i++) {
                try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.FINE, "foo")) { //$NON-NLS-1$
                    try (LogUtils.ScopeLog log1 = new LogUtils.ScopeLog(logger, Level.FINER, "bar")) { //$NON-NLS-1$
                        try (LogUtils.ScopeLog log2 = new LogUtils.ScopeLog(logger, Level.FINEST, "baz")) { //$NON-NLS-1$
                            // do something
                            new Object();
                        }
                    }
                }
            }

            long start = System.nanoTime();
            for (long i = 0; i < runs; i++) {
                try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.FINE, "foo", "run", runs, "test", i)) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    try (LogUtils.ScopeLog log1 = new LogUtils.ScopeLog(logger, Level.FINER, "bar", "run", runs, "test", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                            i)) {
                        try (LogUtils.ScopeLog log2 = new LogUtils.ScopeLog(logger, Level.FINEST, "baz", "run", runs, //$NON-NLS-1$ //$NON-NLS-2$
                                "test", i)) { //$NON-NLS-1$
                            // do something
                            new Object();
                        }
                    }
                }
            }
            /*
             * Note, that the instances of LogRecord created by OldLogUtils.ScopeLog won't be written to disk by the
             * AsyncFileHandler since they are not instances of TraceEventLogRecord.
             */
            long end = System.nanoTime();
            asyncNew.add(end - start);
            for (long i = 0; i < warmUp; i++) {
                try (OldLogUtils.ScopeLog log = new OldLogUtils.ScopeLog(logger, Level.FINE, "foo")) { //$NON-NLS-1$
                    try (OldLogUtils.ScopeLog log1 = new OldLogUtils.ScopeLog(logger, Level.FINER, "bar")) { //$NON-NLS-1$
                        try (OldLogUtils.ScopeLog log2 = new OldLogUtils.ScopeLog(logger, Level.FINEST, "baz")) { //$NON-NLS-1$
                            // do something
                            new Object();
                        }
                    }
                }
            }

            long start2 = System.nanoTime();
            for (long i = 0; i < runs; i++) {
                try (OldLogUtils.ScopeLog log = new OldLogUtils.ScopeLog(logger, Level.FINE, "foo", "run", runs, "test", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        i)) {
                    try (OldLogUtils.ScopeLog log1 = new OldLogUtils.ScopeLog(logger, Level.FINER, "bar", "run", runs, //$NON-NLS-1$ //$NON-NLS-2$
                            "test", i)) { //$NON-NLS-1$
                        try (OldLogUtils.ScopeLog log2 = new OldLogUtils.ScopeLog(logger, Level.FINEST, "baz", "run", //$NON-NLS-1$ //$NON-NLS-2$
                                runs, "test", i)) { //$NON-NLS-1$
                            // do something
                            new Object();
                        }
                    }
                }
            }
            long end2 = System.nanoTime();
            asyncOld.add(end2 - start2);
        }
        System.out.println("\n\"Regular\" Benchmark Results (csv):"); //$NON-NLS-1$
        System.out.println("Runs(#),SyncOld(ns),SyncNew(ns),AsyncOld(ns),AsyncNew(ns),AsyncNewVsSyncOld(rel perf)"); //$NON-NLS-1$
        for (int i = 0; i < run.size(); i++) {
            float factor = (float)syncOld.get(i) / (float)asyncNew.get(i);
            asyncNewVsSyncOld.add(factor);
            System.out.println(String.format("%d,%d,%d,%d,%d,%.2f", run.get(i), syncOld.get(i), syncNew.get(i), //$NON-NLS-1$
                    asyncOld.get(i), asyncNew.get(i), factor));
        }
        System.out.println("\n\"Regular\" Benchmark Results (Human-readable):"); //$NON-NLS-1$
        System.out.println("Runs(#)   SyncOld(ms)   SyncNew(ms)  AsyncOld(ms)  AsyncNew(ms)  AsyncNewVsSyncOld(rel perf)"); //$NON-NLS-1$
        for (int i = 0; i < run.size(); i++) {
            float factor = asyncNewVsSyncOld.get(i);
            System.out.println(String.format("%7d %13.2f %13.2f %13.2f %13.2f %27.2fx", run.get(i), syncOld.get(i)*0.000001, syncNew.get(i)*0.000001, //$NON-NLS-1$
                    asyncOld.get(i)*0.000001, asyncNew.get(i)*0.000001, factor));
        }

        for (int i = 0; i < run.size(); i++) {
            float factor = asyncNewVsSyncOld.get(i);
            assertTrue("Runs: " + run.get(i) + " - AsyncNew expected to be much faster vs SyncOld! " +  //$NON-NLS-1$//$NON-NLS-2$
                    "Expected factor: > " + newAsyncPerformenceThreshold + "x, Actual: " + factor, //$NON-NLS-1$ //$NON-NLS-2$
                    (factor > newAsyncPerformenceThreshold));
        }
    }

    private static long linecount(Path path) throws IOException {
        long linecount = 0;
        try (Stream<String> stream = Files.lines(path, StandardCharsets.UTF_8)) {
            linecount = stream.count();
        }
        return linecount;
    }

    /**
     * Wait for both files to finish
     */
    @After
    public void waiting() {
        try {
            int nbRetries = 0;
            int maxRetries = 25;
            /*
             * Note, that files[1] has half of number of events than files[0] because the instances of LogRecord created by
             * OldLogUtils.ScopeLog won't be written to disk by the AsyncFileHandler since they are not instances of
             * TraceEventLogRecord.
             */
            while (linecount(files[0].toPath()) != (2 * linecount(files[1].toPath())) && (nbRetries < maxRetries)) {
                if (oldFileHandler != null) {
                    oldFileHandler.close();
                }
                if (newFileHandler != null) {
                    newFileHandler.close();
                }
                Thread.sleep(100);
                nbRetries++;
            }
            if (nbRetries >= maxRetries) {
                System.out.println("Max retries (" + nbRetries //$NON-NLS-1$
                        + ") reached: line count of file[0]=" + linecount(files[0].toPath()) //$NON-NLS-1$
                        + ", line count of file[1]=" + linecount(files[1].toPath())); //$NON-NLS-1$
            }
        } catch (IOException | InterruptedException e) {
            fail(e.toString());
        }
    }

    /**
     * Test events without fields
     *
     * @throws SecurityException
     *             Won't happen
     * @throws IOException
     *             Won't happen
     */
    @Test
    public void testLeanBench() throws SecurityException, IOException {
        fLogger = Logger.getAnonymousLogger();
        files[0] = File.createTempFile("trace-lean-old", ".json"); //$NON-NLS-1$ //$NON-NLS-2$
        files[0].deleteOnExit();
        files[1] = File.createTempFile("trace-lean-new", ".json"); //$NON-NLS-1$ //$NON-NLS-2$
        files[1].deleteOnExit();
        oldFileHandler = makeFileHandler(files[0].getAbsolutePath());
        fLogger.addHandler(oldFileHandler);
        fLogger.setLevel(Level.ALL);
        Logger logger = fLogger;
        List<Float> asyncNewVsSyncOld = new ArrayList<>();
        List<Long> asyncNew = new ArrayList<>();
        List<Long> asyncOld = new ArrayList<>();
        List<Long> syncNew = new ArrayList<>();
        List<Long> syncOld = new ArrayList<>();
        List<Long> run = new ArrayList<>();
        for (long runs = warmUp; runs < maxRuns; runs *= growth) {
            for (long i = 0; i < warmUp; i++) {
                try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.FINE, "foo")) { //$NON-NLS-1$
                    try (LogUtils.ScopeLog log1 = new LogUtils.ScopeLog(logger, Level.FINER, "bar")) { //$NON-NLS-1$
                        try (LogUtils.ScopeLog log2 = new LogUtils.ScopeLog(logger, Level.FINEST, "baz")) { //$NON-NLS-1$
                            // do something
                            new Object();
                        }
                    }
                }
            }

            long start = System.nanoTime();
            for (long i = 0; i < runs; i++) {
                try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.FINE, "foo")) { //$NON-NLS-1$
                    try (LogUtils.ScopeLog log1 = new LogUtils.ScopeLog(logger, Level.FINER, "bar")) { //$NON-NLS-1$
                        try (LogUtils.ScopeLog log2 = new LogUtils.ScopeLog(logger, Level.FINEST, "baz")) { //$NON-NLS-1$
                            // do something
                            new Object();
                        }
                    }
                }
            }
            long end = System.nanoTime();
            syncNew.add(end - start);
            for (long i = 0; i < warmUp; i++) {
                try (OldLogUtils.ScopeLog log = new OldLogUtils.ScopeLog(logger, Level.FINE, "foo")) { //$NON-NLS-1$
                    try (OldLogUtils.ScopeLog log1 = new OldLogUtils.ScopeLog(logger, Level.FINER, "bar")) { //$NON-NLS-1$
                        try (OldLogUtils.ScopeLog log2 = new OldLogUtils.ScopeLog(logger, Level.FINEST, "baz")) { //$NON-NLS-1$
                            // do something
                            new Object();
                        }
                    }
                }
            }

            long start2 = System.nanoTime();
            for (long i = 0; i < runs; i++) {
                try (OldLogUtils.ScopeLog log = new OldLogUtils.ScopeLog(logger, Level.FINE, "foo")) { //$NON-NLS-1$
                    try (OldLogUtils.ScopeLog log1 = new OldLogUtils.ScopeLog(logger, Level.FINER, "bar")) { //$NON-NLS-1$
                        try (OldLogUtils.ScopeLog log2 = new OldLogUtils.ScopeLog(logger, Level.FINEST, "baz")) { //$NON-NLS-1$
                            // do something
                            new Object();
                        }
                    }
                }
            }
            long end2 = System.nanoTime();
            syncOld.add(end2 - start2);
            run.add(runs);
        }
        fLogger.removeHandler(oldFileHandler);
        newFileHandler = makeAsyncFileHandler(files[1].getAbsolutePath());
        fLogger.addHandler(newFileHandler);
        for (long runs = warmUp; runs < maxRuns; runs *= growth) {
            for (long i = 0; i < warmUp; i++) {
                try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.FINE, "foo")) { //$NON-NLS-1$
                    try (LogUtils.ScopeLog log1 = new LogUtils.ScopeLog(logger, Level.FINER, "bar")) { //$NON-NLS-1$
                        try (LogUtils.ScopeLog log2 = new LogUtils.ScopeLog(logger, Level.FINEST, "baz")) { //$NON-NLS-1$
                            // do something
                            new Object();
                        }
                    }
                }
            }

            long start = System.nanoTime();
            for (long i = 0; i < runs; i++) {
                try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.FINE, "foo")) { //$NON-NLS-1$
                    try (LogUtils.ScopeLog log1 = new LogUtils.ScopeLog(logger, Level.FINER, "bar")) { //$NON-NLS-1$
                        try (LogUtils.ScopeLog log2 = new LogUtils.ScopeLog(logger, Level.FINEST, "baz", "run", runs, //$NON-NLS-1$ //$NON-NLS-2$
                                "test", i)) { //$NON-NLS-1$
                            // do something
                            new Object();
                        }
                    }
                }
            }
            long end = System.nanoTime();
            asyncNew.add(end - start);
            for (long i = 0; i < warmUp; i++) {
                try (OldLogUtils.ScopeLog log = new OldLogUtils.ScopeLog(logger, Level.FINE, "foo")) { //$NON-NLS-1$
                    try (OldLogUtils.ScopeLog log1 = new OldLogUtils.ScopeLog(logger, Level.FINER, "bar")) { //$NON-NLS-1$
                        try (OldLogUtils.ScopeLog log2 = new OldLogUtils.ScopeLog(logger, Level.FINEST, "baz")) { //$NON-NLS-1$
                            // do something
                            new Object();
                        }
                    }
                }
            }

            long start2 = System.nanoTime();
            for (long i = 0; i < runs; i++) {
                try (OldLogUtils.ScopeLog log = new OldLogUtils.ScopeLog(logger, Level.FINE, "foo", "run", runs, "test", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        i)) {
                    try (OldLogUtils.ScopeLog log1 = new OldLogUtils.ScopeLog(logger, Level.FINER, "bar")) { //$NON-NLS-1$
                        try (OldLogUtils.ScopeLog log2 = new OldLogUtils.ScopeLog(logger, Level.FINEST, "baz")) { //$NON-NLS-1$
                            // do something
                            new Object();
                        }
                    }
                }
            }
            long end2 = System.nanoTime();
            asyncOld.add(end2 - start2);
        }
        System.out.println("\n\"Lean\" Benchmark Results (csv):"); //$NON-NLS-1$
        System.out.println("Runs(#),SyncOld(ns),SyncNew(ns),AsyncOld(ns),AsyncNew(ns),AsyncNewVsSyncOld(rel perf)"); //$NON-NLS-1$
        for (int i = 0; i < run.size(); i++) {
            float factor = (float)syncOld.get(i) / (float)asyncNew.get(i);
            asyncNewVsSyncOld.add(factor);
            System.out.println(String.format("%d,%d,%d,%d,%d,%.2f", run.get(i), syncOld.get(i), syncNew.get(i), //$NON-NLS-1$
                    asyncOld.get(i), asyncNew.get(i), factor));
        }
        System.out.println("\n\"Lean\" Benchmark Results (Human-readable):"); //$NON-NLS-1$
        System.out.println("Runs(#)   SyncOld(ms)   SyncNew(ms)  AsyncOld(ms)  AsyncNew(ms)  AsyncNewVsSyncOld(rel perf)"); //$NON-NLS-1$
        for (int i = 0; i < run.size(); i++) {
            float factor = asyncNewVsSyncOld.get(i);
            asyncNewVsSyncOld.add(factor);
            System.out.println(String.format("%7d %13.2f %13.2f %13.2f %13.2f %27.2fx", run.get(i), syncOld.get(i)*0.000001, syncNew.get(i)*0.000001, //$NON-NLS-1$
                    asyncOld.get(i)*0.000001, asyncNew.get(i)*0.000001, factor));
        }
        System.out.println("-----\n"); //$NON-NLS-1$
        for (int i = 0; i < run.size(); i++) {
            float factor = asyncNewVsSyncOld.get(i);
            assertTrue("Runs: " + run.get(i) + " - AsyncNewLean expected to be much faster vs SyncOldLean! " + //$NON-NLS-1$ //$NON-NLS-2$
                    "Expected factor: > " + newAsyncPerformenceThreshold + "x, Actual: " + factor, //$NON-NLS-1$ //$NON-NLS-2$
                    (factor > newAsyncPerformenceThreshold));
        }
    }

    private static Handler makeAsyncFileHandler(String path) throws IOException {
        try (InputStream fis = new FileInputStream(new File(
                "./src/test/java/org/eclipse/tracecompass/traceeventlogger/res/benchmarklogging.properties"))) { //$NON-NLS-1$
            LogManager manager = LogManager.getLogManager();
            manager.readConfiguration(fis);
        }
        Handler handler = new AsyncFileHandler(path);
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                return record.getMessage() + ",\n"; //$NON-NLS-1$
            }
        });
        handler.setLevel(Level.ALL);
        return handler;
    }

    private static Handler makeFileHandler(String path) throws IOException {
        FileHandler handler = new FileHandler(path);
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                return record.getMessage() + ",\n"; //$NON-NLS-1$
            }
        });
        handler.setLevel(Level.ALL);
        return handler;
    }
}