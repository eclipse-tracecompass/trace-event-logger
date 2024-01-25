/*******************************************************************************
 * Copyright (c) 2024 Ericsson
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

package org.eclipse.tracecompass.trace_event_logger;

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

import org.eclipse.tracecompass.trace_event_logger.LogUtils.TraceEventLogRecord;

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
	private static final LogRecord closeEvent = new LogRecord(Level.FINEST, "close");
	private FileHandler fileHandler;
	private BlockingQueue<List<LogRecord>> queue;
	private Thread writerThread;
	private int maxSize = 1024;
	private int queueDepth = 10000;
	private int flushRate = 1000;
	private String encoding;
	private Filter filter;
	private ErrorManager errorManager;
	private Formatter formatter;
	private Level level;

	private List<LogRecord> recordBuffer = new ArrayList<>(maxSize);
	private Timer timer = new Timer(false);
	TimerTask task = new TimerTask() {
		@Override
		public void run() {
			if (!recordBuffer.isEmpty()) {
				flush();
			}
		}
	};

	private void configure() {
		LogManager manager = LogManager.getLogManager();

		String cname = getClass().getName();
		String prop = manager.getProperty(cname + ".maxSize");
		maxSize = 1024;
		try {
			maxSize = Integer.parseInt(prop.trim());
		} catch (Exception ex) {
			// we tried!
		}
		if (maxSize < 0) {
			maxSize = 1024;
		}
		queueDepth = 10000;
		prop = manager.getProperty(cname + ".queueDepth");
		try {
			queueDepth = Integer.parseInt(prop.trim());
		} catch (Exception ex) {
			// we tried!
		}
		if (queueDepth < 0) {
			queueDepth = 10000;
		}
		flushRate = 1000;
		prop = manager.getProperty(cname + ".flushRate");
		try {
			flushRate = Integer.parseInt(prop.trim());
		} catch (Exception ex) {
			// we tried!
		}
		if (flushRate < 0) {
			flushRate = 1000;
		}
	}

	public AsyncFileHandler() throws SecurityException, IOException {
		this(""); // let's hope it's configured in logging properties.
	}

	/**
	 * Asynchronous file handler, wraps a {@link FileHandler} behind a thread
	 * 
	 * @param pattern the file pattern
	 * @throws SecurityException
	 * @throws IOException
	 */
	public AsyncFileHandler(String pattern) throws SecurityException, IOException {
		configure();
		fileHandler = new FileHandler(pattern);
		if (encoding != null)
			fileHandler.setEncoding(encoding);
		if (errorManager != null)
			fileHandler.setErrorManager(errorManager);
		if (filter != null)
			fileHandler.setFilter(filter);
		if (level != null)
			fileHandler.setLevel(level);

		queue = new ArrayBlockingQueue<>(queueDepth);
		timer.scheduleAtFixedRate(task, flushRate, flushRate);
		writerThread = new Thread(() -> {
			try {
				while (true) {
					List<LogRecord> logRecords = queue.take();
					for (LogRecord logRecord : logRecords) {
						if (logRecord == closeEvent) {
							fileHandler.flush();
							fileHandler.close();
							return;
						} else {
							fileHandler.publish(logRecord);
						}
					}
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		writerThread.setName("AsyncFileHandler Writer");
		writerThread.start();
	}

	@Override
	public synchronized void setEncoding(String encoding) throws SecurityException, UnsupportedEncodingException {
		if (fileHandler != null)
			fileHandler.setEncoding(encoding);
		this.encoding = encoding;
	}

	@Override
	public synchronized void setErrorManager(ErrorManager em) {
		if (fileHandler != null)
			fileHandler.setErrorManager(em);
		this.errorManager = em;
	}

	@Override
	public synchronized void setFilter(Filter newFilter) throws SecurityException {
		if (fileHandler != null)
			fileHandler.setFilter(newFilter);
		this.filter = newFilter;
	}

	@Override
	public synchronized void setFormatter(Formatter newFormatter) throws SecurityException {
		if (fileHandler != null)
			fileHandler.setFormatter(newFormatter);
		this.formatter = formatter;
	}

	@Override
	public synchronized void setLevel(Level newLevel) throws SecurityException {
		if (fileHandler != null)
			fileHandler.setLevel(newLevel);
		this.level = newLevel;
	}

	@Override
	public synchronized void close() throws SecurityException {
		try {
			recordBuffer.add(closeEvent);
			queue.put(recordBuffer);
			writerThread.join();
			timer.cancel();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		super.close();
	}

	@Override
	public synchronized void flush() {
		try {
			queue.put(recordBuffer);
			recordBuffer = new ArrayList<>(maxSize);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public Filter getFilter() {
		return fileHandler.getFilter();
	}

	@Override
	public String getEncoding() {
		return fileHandler.getEncoding();
	}

	@Override
	public Formatter getFormatter() {
		return fileHandler.getFormatter();
	}

	@Override
	public Level getLevel() {
		return fileHandler.getLevel();
	}

	/**
	 * Override me please
	 */
	@Override
	public boolean isLoggable(LogRecord record) {
		return super.isLoggable(record) && (record instanceof TraceEventLogRecord); // add feature switch here
	}

	@Override
	public ErrorManager getErrorManager() {
		return fileHandler.getErrorManager();
	}

	@Override
	public synchronized void publish(LogRecord record) {
		try {
			recordBuffer.add(record);
			if (recordBuffer.size() >= maxSize && isLoggable(record)) {
				queue.put(recordBuffer);
				recordBuffer = new ArrayList<>(maxSize);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
