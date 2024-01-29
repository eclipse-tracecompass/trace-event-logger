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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test cases for snapshotter
 *
 * @author Matthew Khouzam
 */
public class SnapshotTest {

	private Logger fLogger;
	private SnapshotHandler fStreamHandler;

	/**
	 * Default ctor
	 */
	public SnapshotTest() {
		// Do nothing
	}

	/**
	 * Setup function
	 *
	 * @throws SecurityException won't happen
	 * @throws IOException       won't happen
	 */
	@Before
	public void before() throws SecurityException, IOException {
		fLogger = Logger.getAnonymousLogger();
		fStreamHandler = new SnapshotHandler(0.5);
		for (Handler handler : fLogger.getHandlers()) {
			fLogger.removeHandler(handler);
		}
		fLogger.addHandler(fStreamHandler);
		fLogger.setLevel(Level.ALL);
		fStreamHandler.setLevel(Level.ALL);
	}

	/**
	 * Test something too fast for the snapshot
	 */
	@Test
	public void fastTest() {
		Logger logger = this.fLogger;
		assertNotNull(logger);
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "", 0L, 'a', "Bla")); //$NON-NLS-1$ //$NON-NLS-2$
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "", 0L, 'B', "Bla")); //$NON-NLS-1$ //$NON-NLS-2$
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "", 0L, 'E', "Bla")); //$NON-NLS-1$ //$NON-NLS-2$
		fStreamHandler.flush();
	}

	/**
	 * Test malformed events
	 */
	@Test
	public void badTest() {
		Logger logger = this.fLogger;
		assertNotNull(logger);
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "", 0L, 'a')); //$NON-NLS-1$
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "", 0L, 'B')); //$NON-NLS-1$
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "", 0L, 'E')); //$NON-NLS-1$
		fStreamHandler.flush();
	}

	/**
	 * Test an actual snapshot
	 *
	 * @throws InterruptedException won't happen
	 * @throws IOException          won't happen
	 */
	@Test
	public void slowTest() throws InterruptedException, IOException {
		Logger logger = this.fLogger;
		assertNotNull(logger);
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "\"a\"", 10000000000L, 'a', "Bla")); //$NON-NLS-1$ //$NON-NLS-2$
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "\"b\"", 20000000000L, 'B', "Bla")); //$NON-NLS-1$ //$NON-NLS-2$
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "\"c\"", 30000000000L, 'c', "Bla")); //$NON-NLS-1$ //$NON-NLS-2$
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "\"d\"", 40000000000L, 'd', "Bla")); //$NON-NLS-1$ //$NON-NLS-2$
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "\"e\"", 50000000000L, 'e', "Bla")); //$NON-NLS-1$ //$NON-NLS-2$
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "\"f\"", 60000000000L, 'f', "Bla")); //$NON-NLS-1$ //$NON-NLS-2$
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "\"g\"", 70000000000L, 'E', "Bla")); //$NON-NLS-1$ //$NON-NLS-2$
        Thread.sleep(1000);
		fStreamHandler.flush();
		File input = new File("request-10000000.json"); //$NON-NLS-1$
		try (FileReader fr = new FileReader(input)) {
			char[] data = new char[(int) input.length()];
			fr.read(data);
			assertEquals("[\"a\",\n" + "\"b\",\n" + "\"c\",\n" + "\"d\",\n" + "\"e\",\n" + "\"f\",\n" + "\"g\"]", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
					String.valueOf(data));
		}
	}

	/**
	 * Test disabling the snapshotter
	 */
	@Test
	public void testEnableDisable() {
		Logger logger = this.fLogger;
		assertNotNull(logger);
		assertTrue(fStreamHandler.isEnabled());
		fStreamHandler.setEnabled(false);
		assertFalse(fStreamHandler.isEnabled());
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "\"a\"", 10000000001L, 'a', "Bla")); //$NON-NLS-1$ //$NON-NLS-2$
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "\"b\"", 20000000000L, 'B', "Bla")); //$NON-NLS-1$ //$NON-NLS-2$
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "\"c\"", 30000000000L, 'c', "Bla")); //$NON-NLS-1$ //$NON-NLS-2$
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "\"d\"", 40000000000L, 'd', "Bla")); //$NON-NLS-1$ //$NON-NLS-2$
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "\"e\"", 50000000000L, 'e', "Bla")); //$NON-NLS-1$ //$NON-NLS-2$
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "\"f\"", 60000000000L, 'f', "Bla")); //$NON-NLS-1$ //$NON-NLS-2$
		logger.log(new LogUtils.TraceEventLogRecord(Level.INFO, () -> "\"g\"", 70000000000L, 'E', "Bla")); //$NON-NLS-1$ //$NON-NLS-2$
		fStreamHandler.flush();
		File input = new File("request-10000001.json"); //$NON-NLS-1$
		assertFalse(input.exists());
	}

	/**
	 * Test with simple configuration
	 */
	@Test
	public void testConfigure() {
		try (InputStream fis = new FileInputStream(
				new File("./src/test/java/org/eclipse/tracecompass/trace_event_logger/res/logging.properties"))) { //$NON-NLS-1$
			LogManager manager = LogManager.getLogManager();
			manager.readConfiguration(fis);
			Handler first = new SnapshotHandler();
			first.close();
		} catch (FileNotFoundException e) {
			fail(e.getMessage());
		} catch (IOException e) {
			fail(e.getMessage());
		}
	}

	/**
	 * Test with good configuration
	 */
	@Test
	public void testGoodConfigure() {
		try (InputStream fis = new FileInputStream(
				new File("./src/test/java/org/eclipse/tracecompass/trace_event_logger/res/goodlogging.properties"))) { //$NON-NLS-1$
			LogManager manager = LogManager.getLogManager();
			manager.readConfiguration(fis);
			Handler first = new AsyncFileHandler(File.createTempFile("test", ".json").getAbsolutePath()); //$NON-NLS-1$ //$NON-NLS-2$
			first.close();
		} catch (FileNotFoundException e) {
			fail(e.getMessage());
		} catch (IOException e) {
			fail(e.getMessage());
		}
	}

	/**
	 * Test with simple bad config, should still be "OK"
	 *
	 * @throws IOException       won't happen
	 * @throws SecurityException won't happen
	 */
	@Test
	public void testSimpleBadConfigure() throws SecurityException, IOException {
		SnapshotHandler first = new SnapshotHandler(-1);
		assertNotNull(first);
		first.publish(null);

	}

	/**
	 * Test with bad configuration
	 */
	@Test
	public void testBadConfigure() {
		try (InputStream fis = new FileInputStream(
				new File("./src/test/java/org/eclipse/tracecompass/trace_event_logger/res/badlogging.properties"))) { //$NON-NLS-1$
			LogManager manager = LogManager.getLogManager();
			manager.readConfiguration(fis);
			String prop = manager.getProperty("org.eclipse.tracecompass.trace_event_logger.SnapshotHandler.maxEvents"); //$NON-NLS-1$
			assertNotNull(prop);
			Handler first = new SnapshotHandler();
			first.close();
		} catch (FileNotFoundException e) {
			fail(e.getMessage());
		} catch (IOException e) {
			fail(e.getMessage());
		}
	}

	/**
	 * Tear down
	 */
	@After
	public void after() {
		fLogger.removeHandler(fStreamHandler);
	}

}