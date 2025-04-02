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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.ErrorManager;
import java.util.logging.FileHandler;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

import org.eclipse.tracecompass.traceeventlogger.LogUtils.TraceEventLogRecord;

/**
 * Asynchronous File Handler for JUL.
 *
 * This takes the burden of IO and puts it in a worker thread. In stress tests,
 * it is able to have a throughput orders of magnitude greater than the classic
 * {@link FileHandler}. There are caveats though, it requires more CPU time and
 * memory as there is a separate thread and large queue. Moreover, it can get
 * saturated, when this happens the main program may freeze for several
 * milliseconds. Finally, if the worker is still running when the program exist,
 * it will drop anything not written to disk.
 *
 * Parameters to set in logging.properties:
 * <ul>
 * <li>&lt;FileHandler&gt;.level specifies the default level for the
 * {@code Handler} (defaults to {@code Level.ALL}).</li>
 * <li>&lt;FileHandler&gt;.filter specifies the name of a {@code Filter} class
 * to use (defaults to no {@code Filter}).</li>
 * <li>&lt;FileHandler&gt;.formatter specifies the name of a {@code Formatter}
 * class to use (defaults to {@code java.util.logging.XMLFormatter})</li>
 * <li>&lt;FileHandler&gt;.encoding the name of the character set encoding to
 * use (defaults to the default platform encoding).</li>
 * <li>&lt;FileHandler&gt;.limit specifies an approximate maximum amount to
 * write (in bytes) to any one file. If this is zero, then there is no limit.
 * (Defaults to no limit).</li>
 * <li>&lt;FileHandler&gt;.count specifies how many output files to cycle
 * through (defaults to 1).</li>
 * <li>&lt;FileHandler&gt;.pattern specifies a pattern for generating the output
 * file name. See below for details. (Defaults to "%h/java%u.log").</li>
 * <li>&lt;FileHandler&gt;.append specifies whether the FileHandler should
 * append onto any existing files (defaults to false).</li>
 * <li>&lt;FileHandler&gt;.maxLocks specifies the maximum number of concurrent
 * locks held by FileHandler (defaults to 100).</li>
 * <li>&lt;AsyncFileHandler&gt;.maxSize specifies the maximum number of elements
 * to batch into a single request to the writer queue (defaults to 1024).</li>
 * <li>&lt;AsyncFileHandler&gt;.queueDepth specifies the maximum depth of a
 * queue to pass requests to the writer, longer queues are more resilient to
 * spikes but take more memory. (defaults to 10000)</li>
 * <li>&lt;AsyncFileHandler&gt;.flushRate specifies the time in milliseconds
 * between a forced rotation of the buffers, this prevents writer starvation.
 * (defaults to 1000 or 1 second)</li>
 * </ul>
 */
public class AsyncFileHandler extends StreamHandler {
    private static final LogRecord CLOSE_EVENT = new LogRecord(Level.FINEST, "CLOSE_EVENT"); //$NON-NLS-1$
    private FileHandler fFileHandler;
    private BlockingQueue<List<LogRecord>> fQueue;
    private Thread fWriterThread;
    private int fMaxSize = 1024;
    private int fQueueDepth = 10000;
    private int fFlushRate = 1000;
    private String fEncoding;
    private Filter fFilter;
    private ErrorManager fErrorManager;
    private Formatter fFormatter;
    private Level fLevel;

    private List<LogRecord> fRecordBuffer = new ArrayList<>(fMaxSize);
    private Timer fTimer = new Timer(false);
    TimerTask fTask = new TimerTask() {
        @Override
        public void run() {
            if (!fRecordBuffer.isEmpty()) {
                flush();
            }
        }
    };

    private void configure() {
        LogManager manager = LogManager.getLogManager();

        String cname = getClass().getName();
        String prop = manager.getProperty(cname + ".maxSize"); //$NON-NLS-1$
        fMaxSize = 1024;
        try {
            fMaxSize = Integer.parseInt(prop.trim());
        } catch (Exception ex) {
            // we tried!
        }
        if (fMaxSize < 0) {
            fMaxSize = 1024;
        }
        fQueueDepth = 10000;
        prop = manager.getProperty(cname + ".queueDepth"); //$NON-NLS-1$
        try {
            fQueueDepth = Integer.parseInt(prop.trim());
        } catch (Exception ex) {
            // we tried!
        }
        if (fQueueDepth < 0) {
            fQueueDepth = 10000;
        }
        fFlushRate = 1000;
        prop = manager.getProperty(cname + ".formatter"); //$NON-NLS-1$
        try {
            fFormatter = (Formatter) ClassLoader.getSystemClassLoader().loadClass(prop).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            // we tried!
        }
        prop = manager.getProperty(cname + ".filter"); //$NON-NLS-1$
        try {
            fFilter = (Filter) ClassLoader.getSystemClassLoader().loadClass(prop).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            // we tried!
        }

        prop = manager.getProperty(cname + ".encoding"); //$NON-NLS-1$
        try {
            setEncoding(prop);
        } catch (Exception e) {
            // we tried!
        }

        prop = manager.getProperty(cname + ".flushRate"); //$NON-NLS-1$
        try {
            fFlushRate = Integer.parseInt(prop.trim());
        } catch (Exception ex) {
            // we tried!
        }
        if (fFlushRate < 0) {
            fFlushRate = 1000;
        }
    }

    /**
     * Default Constructor
     *
     * @throws SecurityException
     *             if a security manager exists and if the caller does not have
     *             LoggingPermission("control").
     * @throws IOException
     *             if there are IO problems opening the files.
     */
    public AsyncFileHandler() throws SecurityException, IOException {
        this(null); // let's hope it's configured in logging //$NON-NLS-1$
                    // properties.
    }

    /**
     * Asynchronous file handler, wraps a {@link FileHandler} behind a thread
     *
     * @param pattern
     *            the file pattern
     * @throws SecurityException
     *             if a security manager exists and if the caller does not have
     *             LoggingPermission("control").
     * @throws IOException
     *             if there are IO problems opening the files.
     */
    public AsyncFileHandler(String pattern) throws SecurityException, IOException {
        configure();
        fFileHandler = pattern == null ? new FileHandler() : new FileHandler(pattern);
        if (fEncoding != null) {
            fFileHandler.setEncoding(fEncoding);
        }
        if (fErrorManager != null) {
            fFileHandler.setErrorManager(fErrorManager);
        }
        if (fFilter != null) {
            fFileHandler.setFilter(fFilter);
        }
        if (fLevel != null) {
            fFileHandler.setLevel(fLevel);
        }
        if (fFormatter != null) {
            fFileHandler.setFormatter(fFormatter);
        }

        fQueue = new ArrayBlockingQueue<>(fQueueDepth);
        fTimer.scheduleAtFixedRate(fTask, fFlushRate, fFlushRate);
        fWriterThread = new Thread(() -> {
            try {
                while (true) {
                    List<LogRecord> logRecords = fQueue.take();
                    for (LogRecord logRecord : logRecords) {
                        if (logRecord == CLOSE_EVENT) {
                            fFileHandler.flush();
                            fFileHandler.close();
                            return;
                        }
                        fFileHandler.publish(logRecord);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        fWriterThread.setName("AsyncFileHandler Writer"); //$NON-NLS-1$
        fWriterThread.start();
    }

    @Override
    public synchronized void setEncoding(String encoding) throws SecurityException, UnsupportedEncodingException {
        if (fFileHandler != null) {
            fFileHandler.setEncoding(encoding);
        }
        this.fEncoding = encoding;
    }

    @Override
    public synchronized void setErrorManager(ErrorManager em) {
        if (fFileHandler != null) {
            fFileHandler.setErrorManager(em);
        }
        this.fErrorManager = em;
    }

    @Override
    public synchronized void setFilter(Filter newFilter) throws SecurityException {
        if (fFileHandler != null) {
            fFileHandler.setFilter(newFilter);
        }
        this.fFilter = newFilter;
    }

    @Override
    public synchronized void setFormatter(Formatter newFormatter) throws SecurityException {
        if (fFileHandler != null) {
            fFileHandler.setFormatter(newFormatter);
        }
        this.fFormatter = newFormatter;
    }

    @Override
    public synchronized void setLevel(Level newLevel) throws SecurityException {
        if (fFileHandler != null) {
            fFileHandler.setLevel(newLevel);
        }
        this.fLevel = newLevel;
    }

    @Override
    public synchronized void close() throws SecurityException {
        try {
            fRecordBuffer.add(CLOSE_EVENT);
            fQueue.put(fRecordBuffer);
            fWriterThread.join();
            fTimer.cancel();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        super.close();
    }

    @Override
    public synchronized void flush() {
        try {
            fQueue.put(fRecordBuffer);
            fRecordBuffer = new ArrayList<>(fMaxSize);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public Filter getFilter() {
        return fFileHandler.getFilter();
    }

    @Override
    public String getEncoding() {
        return fFileHandler.getEncoding();
    }

    @Override
    public Formatter getFormatter() {
        return fFileHandler.getFormatter();
    }

    @Override
    public Level getLevel() {
        return fFileHandler.getLevel();
    }

    /**
     * Override me please
     */
    @Override
    public boolean isLoggable(LogRecord record) {
        // add feature switch here
        return fFileHandler.isLoggable(record) && (record instanceof TraceEventLogRecord);
    }

    @Override
    public ErrorManager getErrorManager() {
        return fFileHandler.getErrorManager();
    }

    @Override
    public synchronized void publish(LogRecord record) {
        try {
            if (isLoggable(record)) {
                fRecordBuffer.add(record);
                if (fRecordBuffer.size() >= fMaxSize) {
                    fQueue.put(fRecordBuffer);
                    fRecordBuffer = new ArrayList<>(fMaxSize);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
