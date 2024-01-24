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

import java.util.logging.LogRecord;

import org.eclipse.tracecompass.trace_event_logger.LogUtils.TraceEventLogRecord;

class InnerEvent {

	public static InnerEvent create(LogRecord lRecord) {
		if (lRecord instanceof LogUtils.TraceEventLogRecord) {
			TraceEventLogRecord rec = (TraceEventLogRecord) lRecord;
			Object[] parameters = rec.getParameters();
			if (parameters != null && parameters.length >= 3) {
				double ts = ((Long) parameters[0]).longValue()*0.001;
				String phase = String.valueOf(parameters[1]);
				String pid = String.valueOf(parameters[2]);
				String tid = String.valueOf(parameters[2]);
				return new InnerEvent(rec, ts, phase, pid, tid);
			}
		}
		return null;
	}

	private final LogRecord message;
	private final double ts;
	private final String tid;
	private final String pid;
	private final String phase;

	public InnerEvent(LogRecord message, double ts, String phase, String pid, String tid) {
		this.message = message;
		this.ts = ts;
		this.phase = phase;
		this.tid = tid;
		this.pid = pid;
	}

	public String getMessage() {
		return message.getMessage();
	}

	public double getTs() {
		return ts;
	}

	public String getTid() {
		return tid;
	}

	public String getPid() {
		return pid;
	}

	public String getPhase() {
		return phase;
	}
}
