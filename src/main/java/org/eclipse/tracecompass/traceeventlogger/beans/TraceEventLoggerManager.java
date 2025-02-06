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
package org.eclipse.tracecompass.traceeventlogger.beans;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trace Event Logger Monitor, shows the state of every scoped logger
 *
 * Use the {@link #update(String, long)} method to publish a new value
 *
 * @author Matthew Khouzam
 */
public final class TraceEventLoggerManager {

    private final Map<String, TraceEventLoggerBean> fCounters = new LinkedHashMap<>();

    /**
     * Instance, internal, do not use
     */
    private static TraceEventLoggerManager sInstance = null;

    private boolean fEnabled = false;

    /**
     * Constructor
     */
    private TraceEventLoggerManager() {
        String loggingProperty = System.getProperty("enableMonitoring", "false"); //$NON-NLS-1$ //$NON-NLS-2$

        // Convert to boolean
        fEnabled = Boolean.parseBoolean(loggingProperty);
    }

    /**
     * Update a value
     *
     * @param label
     *            the label to update
     * @param value
     *            the value to update for a given label
     */
    public synchronized void update(String label, long value) {
        if (fEnabled) {
            fCounters.computeIfAbsent(label, TraceEventLoggerBean::new).accept(value);
        }
    }

    /**
     * Get the instance of the manager
     *
     * @return the manager
     */
    public static synchronized TraceEventLoggerManager getInstance() {
        TraceEventLoggerManager instance = sInstance;
        if (instance == null) {
            instance = new TraceEventLoggerManager();
            sInstance = instance;
        }
        return instance;
    }
}
