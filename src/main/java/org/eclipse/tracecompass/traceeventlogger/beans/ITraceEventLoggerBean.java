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

import javax.management.MXBean;

/**
 * Trace Event Logger Monitor MXBean interface. Needed to publish MXBeans.
 *
 * An MXBean is a managed java object that uses Java Management (JMX) to publish
 * information. MXBeans have a pre-defined datatype, and in this case will be
 * SimpleTypes. This allows the value to be easily plotted.
 *
 * @author Matthew Khouzam
 */
@MXBean
public interface ITraceEventLoggerBean {

    /**
     * Get the observed element name
     *
     * @return the observed element name
     */
    String getObservedElementName();

    /**
     * Get the mean (average) time
     *
     * @return the average time
     */
    double getMeanTime();

    /**
     * Get the total (sum) time
     *
     * @return the sum time
     */
    long getTotalTime();

    /**
     * Get the number of times (count) the element is added
     *
     * @return the count of elements added
     */
    long getCount();

    /**
     * Get the minimum time
     *
     * @return the minimum time
     */
    long getMinTime();

    /**
     * Get the maximum time
     *
     * @return the maximum time
     */
    long getMaxTime();
}
