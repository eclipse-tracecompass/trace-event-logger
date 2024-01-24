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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import org.eclipse.tracecompass.trace_event_logger.LogUtils.TraceEventLogRecord;

/**
 * Snapshot handler, will write to disk in a deferred way. Parameters to
 * configure:
 * <ul>
 * <li>maxEvents (maximum amount of events to write)</li>
 * <li>timeout (maximum amount of time in seconds before this snapshot is
 * triggered)</li>
 * <li>filePath (pattern to write file names e.g. "request-" will yield
 * "request-23.json", "request-24.json"...)</li>
 * <li>isEnabled (set to false to disable)</li>
 * </ul>
 */
public class SnapshotHandler extends FileHandler {

	// the following can be configured by Logging.properties
	private int maxEvents = 1000000;
	private double timeout = 30.0;
	protected String filePath = "request-"; //$NON-NLS-1$
	// Enable scope logs by default
	private volatile boolean isEnabled = true;

	private Deque<InnerEvent> fData = new ArrayDeque<>();
	private Map<String, Map<String, List<InnerEvent>>> fStacks = new HashMap<>();

	/**
	 * Snapshot handler constructor
	 *
	 * @throws IOException       If there is a file error
	 * @throws SecurityException if we don't have the permissions
	 */
	public SnapshotHandler() throws IOException, SecurityException {
		super();
		configure();
	}

	/**
	 * Snapshot handler constructor
	 * 
	 * @param timeout the timeout in seconds
	 *
	 * @throws IOException       If there is a file error
	 * @throws SecurityException if we don't have the permissions
	 */
	public SnapshotHandler(double timeout) throws IOException, SecurityException {
		super();
		configure();
		if (timeout > 0.0) {
			this.timeout = timeout;
		}
	}

	private void configure() {
		LogManager manager = LogManager.getLogManager();

		String cname = getClass().getName();
		String prop = manager.getProperty(cname + ".maxEvents");
		maxEvents = 1000000;
		try {
			maxEvents = Integer.parseInt(prop.trim());
		} catch (Exception ex) {
			// we tried!
		}
		if (maxEvents < 0) {
			maxEvents = 1000000;
		}
		timeout = 10000;
		prop = manager.getProperty(cname + ".timeout");
		try {
			timeout = Double.parseDouble(prop.trim());
		} catch (Exception ex) {
			// we tried!
		}
		if (timeout < 0) {
			timeout = 30.0;
		}
		filePath = "request-";
		prop = manager.getProperty(cname + ".filePath");
		try {
			filePath = prop.trim();
		} catch (Exception ex) {
			// we tried!
		}
	}

	@Override
	public boolean isLoggable(LogRecord record) {
		return (isEnabled && record != null && super.isLoggable(record)
				&& (record.getLevel().intValue() <= Level.FINE.intValue()) && (record instanceof TraceEventLogRecord)); // add
																														// feature
																														// switch
																														// here
	}

	private boolean addToSnapshot(LogRecord message) {
		InnerEvent event = InnerEvent.create(message);
		if (event == null) {
			return false;
		}
		fData.add(event);
		while (fData.size() > maxEvents) {
			fData.remove();
		}
		Map<String, List<InnerEvent>> pidMap = fStacks.computeIfAbsent(event.getPid(), unused -> new HashMap<>());
		List<InnerEvent> stack = pidMap.computeIfAbsent(event.getTid(), unused -> new ArrayList<>());
		String phase = event.getPhase();
		switch (phase) {
		case "B": //$NON-NLS-1$
		{
			stack.add(event);
			break;
		}
		case "E": //$NON-NLS-1$
		{
			InnerEvent lastEvent = stack.remove(stack.size() - 1);
			if (stack.isEmpty()) {
				double delta = (event.getTs() - lastEvent.getTs()) * 0.000001; // convert to seconds
				if (delta > timeout) {
					drain(fData);
				}
			}
			break;
		}
		default:
			// do nothing
		}
		return true;
	}

	@Override
	public synchronized void publish(LogRecord record) {
		if (record != null) {
			addToSnapshot(record);
		}
		super.publish(record);
	}

	private void drain(Deque<InnerEvent> data) {
		Thread thread = new Thread(() -> {
			Path path = new File(filePath + Long.toString((long) data.getFirst().getTs()) + ".json").toPath(); //$NON-NLS-1$
			try (BufferedWriter fw = Files.newBufferedWriter(path, Charset.defaultCharset())) {
				fw.write('[');
				boolean first = true;
				for (InnerEvent event : data) {
					if (first) {
						first = false;
					} else {
						fw.write(',');
						fw.write('\n');
					}
					fw.write(event.getMessage());
				}
				data.clear();
				fw.write(']');
			} catch (IOException e) {
				// we tried!
			}
		});
		thread.setName("Trace Drainer"); //$NON-NLS-1$
		thread.start();
	}

	/**
	 * Enable or disable snapshotter
	 * 
	 * @param isEnabled true is enabled, false is disabled
	 */
	public void setEnabled(Boolean isEnabled) {
		this.isEnabled = isEnabled;
	}

	/**
	 * Is the snapshotter enabled?
	 * 
	 * @return true if enabled
	 */
	public boolean isEnabled() {
		return isEnabled;
	}
}
