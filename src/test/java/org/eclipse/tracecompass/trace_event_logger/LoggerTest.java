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
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import org.eclipse.tracecompass.trace_event_logger.LogUtils.FlowScopeLog;
import org.eclipse.tracecompass.trace_event_logger.LogUtils.FlowScopeLogBuilder;
import org.junit.Before;
import org.junit.Test;

/**
 * Test cases for logger (line sensitive!)
 *
 * @author Matthew Khouzam
 */
public class LoggerTest {

    private StringOutputStream fLog;
    private Logger fLogger;
    private StreamHandler fStreamHandler;

    private static String eventWithNoTs(String event) {
        //"ts":["1530079243191"],"ph":"E","tid":1,"p...>
        return event.replaceFirst("\\\"ts\\\"\\:\\\"\\d*\\.?\\d*\\\"", "\"ts\":0"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static String eventUnifyId(String event) {
        return event.replaceFirst("\\\"id\\\"\\:\\\"0x[0-9A-Fa-f]+\\\"", "\"id\":\"0x1234\""); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static class StringOutputStream extends OutputStream {

        private List<String> fMessages = new ArrayList<>();
        private StringBuilder sb = new StringBuilder();
        private boolean secondLine = false;
        private boolean start = true;

        @Override
        public void write(int b) throws IOException {
            // We don't care about carriage return (Windows). We only need to
            // rely on \n to detect the next line
            if (b == '\r') {
                return;
            }

            if (b != '\n') {
                if (secondLine) {
                    if (start) {
                        sb.append((char) b);
                    }
                    if (b == ',') {
                        start = true;
                    }
                }
            } else {
                if (secondLine) {
                    fMessages.add(eventUnifyId(eventWithNoTs(sb.toString())));
                    sb = new StringBuilder();
                    secondLine = false;
                } else {
                    secondLine = true;
                }
            }
        }

        public List<String> getMessages() {
            return fMessages;
        }
    }

    /**
     * Set up logger
     */
    @Before
    public void before() {
        fLogger = Logger.getAnonymousLogger();
        fLog = new StringOutputStream();
        fStreamHandler = new StreamHandler(fLog, new SimpleFormatter());
        fStreamHandler.setLevel(Level.FINER);
        fLogger.setLevel(Level.ALL);
        fLogger.addHandler(fStreamHandler);
    }

    /**
     * Test simple logging
     */
    @Test
    public void testHelloWorld() {
        Logger logger = fLogger;
        assertNotNull(logger);
        try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.INFO, "world")) { //$NON-NLS-1$
            // do something
            new Object();
        }
        fStreamHandler.flush();
        assertEquals("INFO: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"world\"}", fLog.getMessages().get(0)); //$NON-NLS-1$
        assertEquals("INFO: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1}", fLog.getMessages().get(1)); //$NON-NLS-1$
    }

    /**
     * Test nesting
     */
    @Test
    public void testNesting() {
        Logger logger = fLogger;
        assertNotNull(logger);
        try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.INFO, "foo")) { //$NON-NLS-1$
            // do something
            new Object();
            try (LogUtils.ScopeLog log1 = new LogUtils.ScopeLog(logger, Level.INFO, "bar")) { //$NON-NLS-1$
                // do something
                new Object();
            }
        }
        fStreamHandler.flush();
        assertEquals("INFO: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"foo\"}", fLog.getMessages().get(0)); //$NON-NLS-1$
        assertEquals("INFO: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"bar\"}", fLog.getMessages().get(1)); //$NON-NLS-1$
        assertEquals("INFO: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1}", fLog.getMessages().get(2)); //$NON-NLS-1$
        assertEquals("INFO: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1}", fLog.getMessages().get(3)); //$NON-NLS-1$
    }

    /**
     * Test nesting with filtering
     */
    @Test
    public void testNestingFiltered() {
        Logger logger = fLogger;
        assertNotNull(logger);
        try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.FINE, "foo")) { //$NON-NLS-1$
            // do something
            new Object();
            try (LogUtils.ScopeLog log1 = new LogUtils.ScopeLog(logger, Level.FINER, "bar")) { //$NON-NLS-1$
                // do something
                new Object();
                try (LogUtils.ScopeLog log2 = new LogUtils.ScopeLog(logger, Level.FINEST, "baz")) { //$NON-NLS-1$
                    // do something
                    new Object();
                }
            }
        }
        fStreamHandler.flush();
        assertEquals("FINE: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"foo\"}", fLog.getMessages().get(0)); //$NON-NLS-1$
        assertEquals("FINER: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"bar\"}", fLog.getMessages().get(1)); //$NON-NLS-1$
        assertEquals("FINER: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1}", fLog.getMessages().get(2)); //$NON-NLS-1$
        assertEquals("FINE: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1}", fLog.getMessages().get(3)); //$NON-NLS-1$
    }

    /**
     * Test nesting with different loglevels
     */
    @Test
    public void testNestingLogLevels() {
        Logger logger = fLogger;
        assertNotNull(logger);
        try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.WARNING, "foo")) { //$NON-NLS-1$
            try (LogUtils.ScopeLog log1 = new LogUtils.ScopeLog(logger, Level.FINE, "bar")) { //$NON-NLS-1$
                // do something
                new Object();
            }
        }
        fStreamHandler.flush();
        assertEquals("WARNING: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"foo\"}", //$NON-NLS-1$
                fLog.getMessages().get(0));
        assertEquals("FINE: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"bar\"}", fLog.getMessages().get(1)); //$NON-NLS-1$
        assertEquals("FINE: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1}", fLog.getMessages().get(2)); //$NON-NLS-1$
        assertEquals("WARNING: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1}", fLog.getMessages().get(3)); //$NON-NLS-1$
    }

    /**
     * Test nesting with additional data
     */
    @Test
    public void testNestingWithData() {
        Logger logger = fLogger;
        assertNotNull(logger);
        try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.WARNING, "foo")) { //$NON-NLS-1$
            try (LogUtils.ScopeLog log1 = new LogUtils.ScopeLog(logger, Level.FINE, "bar")) { //$NON-NLS-1$
                // do something
                log1.addData("return", false); //$NON-NLS-1$
            }
        }
        fStreamHandler.flush();
        assertEquals("WARNING: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"foo\"}", //$NON-NLS-1$
                fLog.getMessages().get(0));
        assertEquals("FINE: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"bar\"}", fLog.getMessages().get(1)); //$NON-NLS-1$
        assertEquals("FINE: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1,\"args\":{\"return\":\"false\"}}", fLog.getMessages().get(2)); //$NON-NLS-1$
        assertEquals("WARNING: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1}", fLog.getMessages().get(3)); //$NON-NLS-1$
    }

    /**
     * Test flow with filtering
     */
    @Test
    public void testFlowFiltered() {
        Logger logger = fLogger;
        assertNotNull(logger);
        try (FlowScopeLog log = new FlowScopeLogBuilder(logger, Level.FINE, "foo").setCategory("mycat").build()) { //$NON-NLS-1$ //$NON-NLS-2$
            // do something
            new Object();
            try (FlowScopeLog log1 = new FlowScopeLogBuilder(logger, Level.FINER, "bar", "big", "ben").setParentScope(log).build()) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                // do something
                new Object();
                try (FlowScopeLog log2 = new FlowScopeLogBuilder(logger, Level.FINEST, "baz").setParentScope(log1).build()) { //$NON-NLS-1$
                    // do something
                    new Object();
                }
            }
        }
        fStreamHandler.flush();
        assertEquals("FINE: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"foo\"}", fLog.getMessages().get(0)); //$NON-NLS-1$
        assertEquals("FINE: {\"ts\":0,\"ph\":\"s\",\"tid\":1,\"pid\":1,\"name\":\"foo\",\"cat\":\"mycat\",\"id\":\"0x1234\"}", fLog.getMessages().get(1)); //$NON-NLS-1$
        assertEquals("FINER: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"bar\",\"args\":{\"big\":\"ben\"}}", fLog.getMessages().get(2)); //$NON-NLS-1$
        assertEquals("FINER: {\"ts\":0,\"ph\":\"t\",\"tid\":1,\"pid\":1,\"name\":\"bar\",\"cat\":\"mycat\",\"id\":\"0x1234\",\"args\":{\"big\":\"ben\"}}", fLog.getMessages().get(3)); //$NON-NLS-1$
        assertEquals("FINER: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1}", fLog.getMessages().get(4)); //$NON-NLS-1$
        assertEquals("FINE: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1}", fLog.getMessages().get(5)); //$NON-NLS-1$
    }

    /**
     * Test flow with different loglevels
     */
    @Test
    public void testFlowLogLevels() {
        Logger logger = fLogger;
        assertNotNull(logger);
        try (FlowScopeLog log = new FlowScopeLogBuilder(logger, Level.WARNING, "foo").setCategory("mydog").build()) { //$NON-NLS-1$ //$NON-NLS-2$
            try (FlowScopeLog log1 = new FlowScopeLogBuilder(logger, Level.FINE, "bar").setParentScope(log).build()) { //$NON-NLS-1$
                log1.step("barked"); //$NON-NLS-1$
                new Object();
            }
        }
        fStreamHandler.flush();
        assertEquals("WARNING: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"foo\"}", //$NON-NLS-1$
                fLog.getMessages().get(0));
        assertEquals("WARNING: {\"ts\":0,\"ph\":\"s\",\"tid\":1,\"pid\":1,\"name\":\"foo\",\"cat\":\"mydog\",\"id\":\"0x1234\"}", //$NON-NLS-1$
                fLog.getMessages().get(1));
        assertEquals("FINE: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"bar\"}", //$NON-NLS-1$
                fLog.getMessages().get(2));
        assertEquals("FINE: {\"ts\":0,\"ph\":\"t\",\"tid\":1,\"pid\":1,\"name\":\"bar\",\"cat\":\"mydog\",\"id\":\"0x1234\"}", fLog.getMessages().get(3)); //$NON-NLS-1$
        assertEquals("FINE: {\"ts\":0,\"ph\":\"t\",\"tid\":1,\"pid\":1,\"name\":\"barked\",\"cat\":\"mydog\",\"id\":\"0x1234\"}", fLog.getMessages().get(4)); //$NON-NLS-1$
        assertEquals("FINE: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1}", fLog.getMessages().get(5)); //$NON-NLS-1$
        assertEquals("WARNING: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1}", fLog.getMessages().get(6)); //$NON-NLS-1$
    }

    /**
     * Test flow where child flow sets category and id instead of parent
     */
    @Test
    public void testFlowWithUnsetParent() {
        Logger logger = fLogger;
        assertNotNull(logger);
        try (FlowScopeLog log = new FlowScopeLogBuilder(logger, Level.WARNING, "foo").setCategory("mydog").build()) { //$NON-NLS-1$ //$NON-NLS-2$
            try (FlowScopeLog log1 = new FlowScopeLogBuilder(logger, Level.FINE, "bar").setCategoryAndId("mydog", log.getId()).build()) { //$NON-NLS-1$ //$NON-NLS-2$
                log1.step("barked"); //$NON-NLS-1$
                new Object();
            }
        }
        fStreamHandler.flush();
        assertEquals("WARNING: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"foo\"}", //$NON-NLS-1$
                fLog.getMessages().get(0));
        assertEquals("WARNING: {\"ts\":0,\"ph\":\"s\",\"tid\":1,\"pid\":1,\"name\":\"foo\",\"cat\":\"mydog\",\"id\":\"0x1234\"}", //$NON-NLS-1$
                fLog.getMessages().get(1));
        assertEquals("FINE: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"bar\"}", //$NON-NLS-1$
                fLog.getMessages().get(2));
        assertEquals("FINE: {\"ts\":0,\"ph\":\"t\",\"tid\":1,\"pid\":1,\"name\":\"bar\",\"cat\":\"mydog\",\"id\":\"0x1234\"}", fLog.getMessages().get(3)); //$NON-NLS-1$
        assertEquals("FINE: {\"ts\":0,\"ph\":\"t\",\"tid\":1,\"pid\":1,\"name\":\"barked\",\"cat\":\"mydog\",\"id\":\"0x1234\"}", fLog.getMessages().get(4)); //$NON-NLS-1$
        assertEquals("FINE: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1}", fLog.getMessages().get(5)); //$NON-NLS-1$
        assertEquals("WARNING: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1}", fLog.getMessages().get(6)); //$NON-NLS-1$
    }

    /**
     * Test flow with different loglevels
     */
    @Test
    public void testFlowWithData() {
        Logger logger = fLogger;
        assertNotNull(logger);
        try (FlowScopeLog log = new FlowScopeLogBuilder(logger, Level.WARNING, "foo").setCategory("myspider").build()) { //$NON-NLS-1$ //$NON-NLS-2$
            try (FlowScopeLog log1 = new FlowScopeLogBuilder(logger, Level.FINE, "bar").setParentScope(log).build()) { //$NON-NLS-1$
                // do something
                log1.addData("return", false); //$NON-NLS-1$
            }
        }
        fStreamHandler.flush();
        assertEquals("WARNING: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"foo\"}", //$NON-NLS-1$
                fLog.getMessages().get(0));
        assertEquals("WARNING: {\"ts\":0,\"ph\":\"s\",\"tid\":1,\"pid\":1,\"name\":\"foo\",\"cat\":\"myspider\",\"id\":\"0x1234\"}", //$NON-NLS-1$
                fLog.getMessages().get(1));
        assertEquals("FINE: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"bar\"}", //$NON-NLS-1$
                fLog.getMessages().get(2));
        assertEquals("FINE: {\"ts\":0,\"ph\":\"t\",\"tid\":1,\"pid\":1,\"name\":\"bar\",\"cat\":\"myspider\",\"id\":\"0x1234\"}", fLog.getMessages().get(3)); //$NON-NLS-1$
        assertEquals("FINE: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1,\"args\":{\"return\":\"false\"}}", fLog.getMessages().get(4)); //$NON-NLS-1$
        assertEquals("WARNING: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1}", fLog.getMessages().get(5)); //$NON-NLS-1$
    }

    /**
     * Test the flow scope builder without calling any other method than the
     * constructor
     */
    @Test
    public void testFlowBuilderNoExtra() {
        Logger logger = fLogger;
        assertNotNull(logger);
        try (FlowScopeLog log = new FlowScopeLogBuilder(logger, Level.WARNING, "foo").build()) { //$NON-NLS-1$
            // do something
            new Object();
        }
        fStreamHandler.flush();
        assertEquals("WARNING: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"foo\"}", //$NON-NLS-1$
                fLog.getMessages().get(0));
        assertEquals("WARNING: {\"ts\":0,\"ph\":\"s\",\"tid\":1,\"pid\":1,\"name\":\"foo\",\"cat\":\"null\",\"id\":\"0x1234\"}", //$NON-NLS-1$
                fLog.getMessages().get(1));
        assertEquals("WARNING: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1}", fLog.getMessages().get(2)); //$NON-NLS-1$
    }

    /**
     * Test the flow scope builder calling
     * {@link FlowScopeLogBuilder#setParentScope(FlowScopeLog)}, then
     * {@link FlowScopeLogBuilder#setCategory(String)}.
     */
    @Test(expected = IllegalStateException.class)
    public void testFlowBuilderCatThenParent() {
        Logger logger = fLogger;
        assertNotNull(logger);
        try (FlowScopeLog log = new FlowScopeLogBuilder(logger, Level.WARNING, "foo").setCategory("myspider").build()) { //$NON-NLS-1$ //$NON-NLS-2$
            try (FlowScopeLog log1 = new FlowScopeLogBuilder(logger, Level.FINE, "bar").setParentScope(log).setCategory("myspider").build()) { //$NON-NLS-1$ //$NON-NLS-2$
                // do something
                new Object();
            }
        }
    }

    /**
     * Test the flow scope builder calling
     * {@link FlowScopeLogBuilder#setParentScope(FlowScopeLog)}, then
     * {@link FlowScopeLogBuilder#setCategory(String)}.
     */
    @Test(expected = IllegalStateException.class)
    public void testFlowBuilderParentThenCat() {
        Logger logger = fLogger;
        assertNotNull(logger);
        try (FlowScopeLog log = new FlowScopeLogBuilder(logger, Level.WARNING, "foo").setCategory("myspider").build()) { //$NON-NLS-1$ //$NON-NLS-2$
            try (FlowScopeLog log1 = new FlowScopeLogBuilder(logger, Level.FINE, "bar").setCategory("myspider").setParentScope(log).build()) { //$NON-NLS-1$ //$NON-NLS-2$
                // do something
                new Object();
            }
        }
    }


    /**
     * Test the flow scope builder calling
     * {@link FlowScopeLogBuilder#setParentScope(FlowScopeLog)}, then
     * {@link FlowScopeLogBuilder#setCategory(String)}.
     */
    @Test(expected = IllegalStateException.class)
    public void testFlowBuilderParentThenCatId() {
        Logger logger = fLogger;
        assertNotNull(logger);
        try (FlowScopeLog log = new FlowScopeLogBuilder(logger, Level.WARNING, "foo").setCategory("myspider").build()) { //$NON-NLS-1$ //$NON-NLS-2$
            try (FlowScopeLog log1 = new FlowScopeLogBuilder(logger, Level.FINE, "bar").setParentScope(log).setCategoryAndId("myspider",1).build()) { //$NON-NLS-1$ //$NON-NLS-2$
                // do something
                new Object();
            }
        }
    }

    /**
     * Test the flow scope builder calling
     * {@link FlowScopeLogBuilder#setParentScope(FlowScopeLog)}, then
     * {@link FlowScopeLogBuilder#setCategory(String)}.
     */
    @Test(expected = IllegalStateException.class)
    public void testFlowBuilderCatIdThenParent() {
        Logger logger = fLogger;
        assertNotNull(logger);
        try (FlowScopeLog log = new FlowScopeLogBuilder(logger, Level.WARNING, "foo").setCategory("myspider").build()) { //$NON-NLS-1$ //$NON-NLS-2$
            try (FlowScopeLog log1 = new FlowScopeLogBuilder(logger, Level.FINE, "bar").setCategoryAndId("myspider",1).setParentScope(log).build()) { //$NON-NLS-1$ //$NON-NLS-2$
                // do something
                new Object();
            }
        }
    }


    /**
     * Test nesting with different arguments
     */
    @Test
    public void testAttributes() {
        Logger logger = fLogger;
        assertNotNull(logger);
        try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.WARNING, "foo", "Pen:Pineapple", "Apple:Pen")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            // do something
            new Object();
        }
        try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.WARNING, "foo", "Pen:Pineapple:Apple:Pen")) { //$NON-NLS-1$ //$NON-NLS-2$
            // do something
            new Object();
        }
        try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.WARNING, "foo", "pen", "pineapple", "apple", "pen", "number_of_badgers", 12)) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
            // do something
            new Object();
        }
        fStreamHandler.flush();
        assertEquals("WARNING: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"foo\",\"args\":{\"Pen:Pineapple\":\"Apple:Pen\"}}", //$NON-NLS-1$
                fLog.getMessages().get(0));
        assertEquals("WARNING: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1}", fLog.getMessages().get(1)); //$NON-NLS-1$

        assertEquals("WARNING: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"foo\",\"args\":{\"msg\":\"Pen:Pineapple:Apple:Pen\"}}", //$NON-NLS-1$
                fLog.getMessages().get(2));
        assertEquals("WARNING: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1}", fLog.getMessages().get(3)); //$NON-NLS-1$

        assertEquals("WARNING: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"foo\",\"args\":{\"pen\":\"pineapple\",\"apple\":\"pen\",\"number_of_badgers\":12}}", //$NON-NLS-1$
                fLog.getMessages().get(4));
        assertEquals("WARNING: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1}", fLog.getMessages().get(5)); //$NON-NLS-1$
    }

    /**
     * Test with an odd number of args.
     */
    @Test
    public void testAttributeFail3Args() {
        Logger logger = fLogger;
        assertNotNull(logger);
        try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.WARNING, "foo", "Pen:Pineapple", "Apple", "Pen")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            // do something
            fail("Should be giving an IllegalArgumentException"); //$NON-NLS-1$
        } catch (IllegalArgumentException e) {
        	// pass
        }
    }

    /**
     * Test with a repeating key.
     */
    @Test
    public void testAttributeFailRepeatedArgs() {
        Logger logger = fLogger;
        assertNotNull(logger);
        try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.WARNING, "foo", "badger", "badger", "badger", "badger")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            // do something
            fail("Should be giving an IllegalArgumentException"); //$NON-NLS-1$
        } catch (IllegalArgumentException e) {
			// pass
		}
    }

    /**
     * Test nesting with an exception
     */
    @Test
    public void testNestingException() {
        Logger logger = fLogger;
        assertNotNull(logger);
        try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.INFO, "foo")) { //$NON-NLS-1$
            try (LogUtils.ScopeLog log1 = new LogUtils.ScopeLog(logger, Level.INFO, "bar")) { //$NON-NLS-1$
                // do something
                new Object();
                throw new Exception("test"); //$NON-NLS-1$
            }
        } catch (Exception e) {
            assertEquals("test", e.getMessage()); //$NON-NLS-1$
        }
        fStreamHandler.flush();
        assertEquals("INFO: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"foo\"}", fLog.getMessages().get(0)); //$NON-NLS-1$
        assertEquals("INFO: {\"ts\":0,\"ph\":\"B\",\"tid\":1,\"pid\":1,\"name\":\"bar\"}", fLog.getMessages().get(1)); //$NON-NLS-1$
        assertEquals("INFO: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1}", fLog.getMessages().get(2)); //$NON-NLS-1$
        assertEquals("INFO: {\"ts\":0,\"ph\":\"E\",\"tid\":1,\"pid\":1}", fLog.getMessages().get(3)); //$NON-NLS-1$
    }

    private static final class LivingObject {

        private final Logger fLog;

        public LivingObject(Logger logger) {
            fLog = logger;
            LogUtils.traceObjectCreation(fLog, Level.FINE, this);
        }

        @Deprecated
        @Override
        protected void finalize() throws Throwable {
            LogUtils.traceObjectDestruction(fLog, Level.FINE, this);
            super.finalize();
        }

    }

    /**
     * Test two objects lifecycles
     *
     * @throws Throwable
     *             error in finalizes
     */
    @Test
    public void testObjectLifespan() throws Throwable {
        Logger logger = fLogger;
        assertNotNull(logger);
        {
            LivingObject first = new LivingObject(logger);
            LivingObject second = new LivingObject(logger);
            assertNotNull(first);
            assertNotNull(second);
            // This will surely trigger some static analysis. This is for
            // testing purposes.
            first.finalize();
            second.finalize();
        }

        fStreamHandler.flush();
        assertEquals("FINE: {\"ts\":0,\"ph\":\"N\",\"tid\":1,\"pid\":1,\"name\":\"LivingObject\",\"id\":\"0x1234\"}", fLog.getMessages().get(0)); //$NON-NLS-1$
        assertEquals("FINE: {\"ts\":0,\"ph\":\"N\",\"tid\":1,\"pid\":1,\"name\":\"LivingObject\",\"id\":\"0x1234\"}", fLog.getMessages().get(1)); //$NON-NLS-1$
        assertEquals("FINE: {\"ts\":0,\"ph\":\"D\",\"tid\":1,\"pid\":1,\"name\":\"LivingObject\",\"id\":\"0x1234\"}", fLog.getMessages().get(2)); //$NON-NLS-1$
        assertEquals("FINE: {\"ts\":0,\"ph\":\"D\",\"tid\":1,\"pid\":1,\"name\":\"LivingObject\",\"id\":\"0x1234\"}", fLog.getMessages().get(3)); //$NON-NLS-1$
    }

    /**
     * Test two objects lifecycles
     */
    @Test
    public void testCollectionLifespan() {
        Logger logger = fLogger;
        assertNotNull(logger);
        {
            List<String> avengers = new ArrayList<>();
            int uniqueID = LogUtils.traceObjectCreation(logger, Level.FINE, avengers);
            avengers.add("Cap"); //$NON-NLS-1$
            avengers.add("Arrow"); //$NON-NLS-1$
            avengers.add("Thor"); //$NON-NLS-1$
            avengers.add("Iron"); //$NON-NLS-1$
            LogUtils.traceObjectDestruction(logger, Level.FINE, avengers, uniqueID);

        }

        fStreamHandler.flush();
        assertEquals("FINE: {\"ts\":0,\"ph\":\"N\",\"tid\":1,\"pid\":1,\"name\":\"ArrayList\",\"id\":\"0x1234\"}", fLog.getMessages().get(0)); //$NON-NLS-1$
        assertEquals("FINE: {\"ts\":0,\"ph\":\"D\",\"tid\":1,\"pid\":1,\"name\":\"ArrayList\",\"id\":\"0x1234\"}", fLog.getMessages().get(1)); //$NON-NLS-1$
    }

    /**
     * Test instant events
     */
    @Test
    public void testInstant() {
        Logger logger = fLogger;
        assertNotNull(logger);
        LogUtils.traceInstant(logger, Level.FINE, "hello", "foo", "bar"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        fStreamHandler.flush();
        assertEquals("FINE: {\"ts\":0,\"ph\":\"i\",\"tid\":1,\"pid\":1,\"name\":\"hello\",\"args\":{\"foo\":\"bar\"}}", fLog.getMessages().get(0)); //$NON-NLS-1$
    }

    /**
     * Test asynchronous messages
     */
    @Test
    public void testAsyncMessages() {
        Logger logger = fLogger;
        assertNotNull(logger);
        LogUtils.traceAsyncStart(logger, Level.FINE, "network connect", "net", 10); //$NON-NLS-1$ //$NON-NLS-2$
        LogUtils.traceAsyncStart(logger, Level.FINER, "network lookup", "net", 10); //$NON-NLS-1$ //$NON-NLS-2$
        LogUtils.traceAsyncNested(logger, Level.FINER, "network cache", "net", 10); //$NON-NLS-1$ //$NON-NLS-2$
        // anon message
        LogUtils.traceAsyncStart(logger, Level.FINER, null, null, 0);
        LogUtils.traceAsyncEnd(logger, Level.FINER, null, null, 0);

        LogUtils.traceAsyncEnd(logger, Level.FINER, "network lookup", "net", 10, "OK"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        LogUtils.traceAsyncEnd(logger, Level.FINE, "network connect", "net", 10, "OK"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        fStreamHandler.flush();
        assertEquals("FINE: {\"ts\":0,\"ph\":\"b\",\"tid\":1,\"pid\":1,\"name\":\"network connect\",\"cat\":\"net\",\"id\":\"0x1234\"}", fLog.getMessages().get(0)); //$NON-NLS-1$
        assertEquals("FINER: {\"ts\":0,\"ph\":\"b\",\"tid\":1,\"pid\":1,\"name\":\"network lookup\",\"cat\":\"net\",\"id\":\"0x1234\"}", fLog.getMessages().get(1)); //$NON-NLS-1$
        assertEquals("FINER: {\"ts\":0,\"ph\":\"n\",\"tid\":1,\"pid\":1,\"name\":\"network cache\",\"cat\":\"net\",\"id\":\"0x1234\"}", fLog.getMessages().get(2)); //$NON-NLS-1$
        assertEquals("FINER: {\"ts\":0,\"ph\":\"b\",\"tid\":1,\"pid\":1,\"id\":\"0x1234\"}", fLog.getMessages().get(3)); //$NON-NLS-1$
        assertEquals("FINER: {\"ts\":0,\"ph\":\"e\",\"tid\":1,\"pid\":1,\"id\":\"0x1234\"}", fLog.getMessages().get(4)); //$NON-NLS-1$
        assertEquals("FINER: {\"ts\":0,\"ph\":\"e\",\"tid\":1,\"pid\":1,\"name\":\"network lookup\",\"cat\":\"net\",\"id\":\"0x1234\",\"args\":{\"msg\":\"OK\"}}", fLog.getMessages().get(5)); //$NON-NLS-1$
        assertEquals("FINE: {\"ts\":0,\"ph\":\"e\",\"tid\":1,\"pid\":1,\"name\":\"network connect\",\"cat\":\"net\",\"id\":\"0x1234\",\"args\":{\"msg\":\"OK\"}}", fLog.getMessages().get(6)); //$NON-NLS-1$
    }

    /**
     * Test that null values in arguments are properly handled
     */
    @Test
    public void testNullArguments() {
        Logger logger = fLogger;
        assertNotNull(logger);
        LogUtils.traceInstant(logger, Level.INFO, "test null value", "nullvalue", null); //$NON-NLS-1$ //$NON-NLS-2$
        LogUtils.traceInstant(logger, Level.INFO, "test null key", null, "value"); //$NON-NLS-1$ //$NON-NLS-2$

        fStreamHandler.flush();
        assertEquals("INFO: {\"ts\":0,\"ph\":\"i\",\"tid\":1,\"pid\":1,\"name\":\"test null value\",\"args\":{\"nullvalue\":\"null\"}}", fLog.getMessages().get(0)); //$NON-NLS-1$
        assertEquals("INFO: {\"ts\":0,\"ph\":\"i\",\"tid\":1,\"pid\":1,\"name\":\"test null key\",\"args\":{\"null\":\"value\"}}", fLog.getMessages().get(1)); //$NON-NLS-1$
    }

    /**
     * Test counters
     */
    @Test
    public void testCounter() {
        Logger logger = fLogger;
        assertNotNull(logger);

        LogUtils.traceCounter(logger, Level.FINER, "counter", "cats", 0); //$NON-NLS-1$ //$NON-NLS-2$
        LogUtils.traceCounter(logger, Level.FINER, "counter", "cats", 10); //$NON-NLS-1$ //$NON-NLS-2$
        LogUtils.traceCounter(logger, Level.FINER, "counter", "cats", 0); //$NON-NLS-1$ //$NON-NLS-2$

        fStreamHandler.flush();
        assertEquals("FINER: {\"ts\":0,\"ph\":\"C\",\"tid\":1,\"pid\":1,\"name\":\"counter\",\"args\":{\"cats\":0}}", fLog.getMessages().get(0)); //$NON-NLS-1$
        assertEquals("FINER: {\"ts\":0,\"ph\":\"C\",\"tid\":1,\"pid\":1,\"name\":\"counter\",\"args\":{\"cats\":10}}", fLog.getMessages().get(1)); //$NON-NLS-1$
        assertEquals("FINER: {\"ts\":0,\"ph\":\"C\",\"tid\":1,\"pid\":1,\"name\":\"counter\",\"args\":{\"cats\":0}}", fLog.getMessages().get(2)); //$NON-NLS-1$
    }

    /**
     * Test Marker
     */
    @Test
    public void testMarker() {
        Logger logger = fLogger;
        assertNotNull(logger);
        LogUtils.traceMarker(logger, Level.CONFIG, "instant", 0); //$NON-NLS-1$
        LogUtils.traceMarker(logger, Level.CONFIG, "colored", 15, "color", 0xaabccdd); //$NON-NLS-1$ //$NON-NLS-2$
        fStreamHandler.flush();
        assertEquals("CONFIG: {\"ts\":0,\"ph\":\"R\",\"tid\":1,\"pid\":1,\"name\":\"instant\",\"dur\":0}", fLog.getMessages().get(0)); //$NON-NLS-1$
        assertEquals("CONFIG: {\"ts\":0,\"ph\":\"R\",\"tid\":1,\"pid\":1,\"name\":\"colored\",\"dur\":15,\"args\":{\"color\":179031261}}", fLog.getMessages().get(1)); //$NON-NLS-1$
    }

}
