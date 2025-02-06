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

import java.lang.management.ManagementFactory;
import java.util.LongSummaryStatistics;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;

/**
 * Trace Event Logger internal bean.
 *
 * A bean is a java standard object to publish information.
 *
 * Used to publish performance metrics and KPIs, can be seen with tools such as
 * visualvm and jconsole.
 *
 * This class is internal, it should not be extended or made into API.
 *
 * @author Matthew Khouzam
 */
public final class TraceEventLoggerBean extends NotificationBroadcasterSupport implements ITraceEventLoggerBean {

    private final LongSummaryStatistics fStats = new LongSummaryStatistics();
    private final String fLabel;

    /**
     * Constructor
     *
     * @param label
     *            the name of the bean, colons (':') will be replaced with
     *            hyphens ('-')
     */
    public TraceEventLoggerBean(String label) {
        fLabel = label;
        /**
         * Override potentially finer logging for these, as this breaks the
         * resulting JSON trace file. This happens upon @{link
         * ManagementFactory} use below. Use the default @{link Level.FINE},
         * which doesn't output any such breaking strings. Finer logging for
         * this package isn't necessary anyway here.
         */
        Logger.getLogger("javax.management").setLevel(Level.FINE); //$NON-NLS-1$ NOSONAR
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        String beanName = "org.eclipse.tracecompass.log:type=TraceEventLoggerBean,name=" + label.replace(':', '-'); //$NON-NLS-1$
        try {
            ObjectName name = new ObjectName(beanName);
            mbs.registerMBean(this, name);
        } catch (JMException e) {
            java.util.logging.Logger.getAnonymousLogger().log(Level.WARNING, "Cannot create bean", e); //$NON-NLS-1$
        }
    }

    @Override
    public String getObservedElementName() {
        return fLabel;
    }

    @Override
    public double getMeanTime() {
        return fStats.getAverage();
    }

    @Override
    public long getMinTime() {
        return fStats.getMin();
    }

    @Override
    public long getMaxTime() {
        return fStats.getMax();
    }

    @Override
    public long getTotalTime() {
        return fStats.getSum();
    }

    @Override
    public long getCount() {
        return fStats.getCount();
    }

    /**
     * Accept a long to aggregate
     *
     * @param value
     *            the value to aggregate
     */
    public void accept(long value) {
        fStats.accept(value);
    }
}
