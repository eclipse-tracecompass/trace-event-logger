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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test cases for snapshotter
 *
 * @author Matthew Khouzam
 */
public class SnapshotTest {

	private Logger logger;
	private StreamHandler streamHandler;

	public SnapshotTest() {
		// Do nothing
	}

	@Before
	public void before() throws SecurityException, IOException {
		logger = Logger.getAnonymousLogger();
		streamHandler = new SnapshotHandler(0.5);
		for (Handler handler : logger.getHandlers()) {
			logger.removeHandler(handler);
		}
		logger.addHandler(streamHandler);
		logger.setLevel(Level.ALL);
		streamHandler.setLevel(Level.ALL);
	}

	@Test
	public void fastTest() {
		Logger logger = this.logger;
		assertNotNull(logger);
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "", 0L, 'a', "Bla"));
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "", 0L, 'B', "Bla"));
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "", 0L, 'E', "Bla"));
		streamHandler.flush();
	}

	@Test
	public void slowTest() throws InterruptedException, IOException {
		Logger logger = this.logger;
		assertNotNull(logger);
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "\"a\"", 10000000000L, 'a', "Bla"));
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "\"b\"", 20000000000L, 'B', "Bla"));
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "\"c\"", 30000000000L, 'c', "Bla"));
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "\"d\"", 40000000000L, 'd', "Bla"));
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "\"e\"", 50000000000L, 'e', "Bla"));
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "\"f\"", 60000000000L, 'f', "Bla"));
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "\"g\"", 70000000000L, 'E', "Bla"));
		Thread.currentThread().sleep(1000);
		streamHandler.flush();
		File input = new File("request-10000000.json");
		try (FileReader fr = new FileReader(input)) {
			char[] data = new char[(int) input.length()];
			fr.read(data);
			assertEquals("[\"a\",\n" + "\"b\",\n" + "\"c\",\n" + "\"d\",\n" + "\"e\",\n" + "\"f\",\n" + "\"g\"]", String.valueOf(data));
		}
	}

	@After
	public void after() {
		logger.removeHandler(streamHandler);
	}

}