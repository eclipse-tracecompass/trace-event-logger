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

import java.util.logging.LogRecord;

import org.eclipse.tracecompass.traceeventlogger.LogUtils.TraceEventLogRecord;

class InnerEvent {

    public static InnerEvent create(LogRecord lRecord) {
        if (lRecord instanceof LogUtils.TraceEventLogRecord) {
            TraceEventLogRecord rec = (TraceEventLogRecord) lRecord;
            Object[] parameters = rec.getParameters();
            if (parameters != null && parameters.length >= 3) {
                double ts = ((Long) parameters[0]).longValue() * 0.001;
                String phase = String.valueOf(parameters[1]);
                String pid = String.valueOf(parameters[2]);
                String tid = String.valueOf(parameters[2]);
                return new InnerEvent(rec, ts, phase, pid, tid);
            }
        }
        return null;
    }

    private final LogRecord fMessage;
    private final double fTs;
    private final String fTid;
    private final String fPid;
    private final String fPhase;

    public InnerEvent(LogRecord message, double ts, String phase, String pid, String tid) {
        fMessage = message;
        fTs = ts;
        fPhase = phase;
        fTid = tid;
        fPid = pid;
    }

    public String getMessage() {
        return fMessage.getMessage();
    }

    public double getTs() {
        return fTs;
    }

    public String getTid() {
        return fTid;
    }

    public String getPid() {
        return fPid;
    }

    public String getPhase() {
        return fPhase;
    }
}
