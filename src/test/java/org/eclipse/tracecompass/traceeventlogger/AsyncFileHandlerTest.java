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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import org.junit.After;
import org.junit.Test;

/**
 * Test aynchronous file handler
 */
public class AsyncFileHandlerTest {

    /**
     * Clean up
     */
    @After
    public void after() {
        LogManager.getLogManager().reset();
    }

    /**
     * Test with simple configuration
     */
    @Test
    public void testConfigure() {
        try (InputStream fis = new FileInputStream(
                new File("./src/test/java/org/eclipse/tracecompass/traceeventlogger/res/logging.properties"))) { //$NON-NLS-1$
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
     * Test with good configuration
     */
    @Test
    public void testGoodConfigure() {
        try (InputStream fis = new FileInputStream(
                new File("./src/test/java/org/eclipse/tracecompass/traceeventlogger/res/goodlogging.properties"))) { //$NON-NLS-1$
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
     * Test Bad configuration
     *
     * @throws IOException
     *             The desired exception
     * @throws SecurityException
     *             won't happen
     */
    @Test
    public void testBadConfigure() throws SecurityException, IOException {
        try (InputStream fis = new FileInputStream(
                new File("./src/test/java/org/eclipse/tracecompass/traceeventlogger/res/badlogging.properties"))) { //$NON-NLS-1$
            LogManager manager = LogManager.getLogManager();
            try {
                manager.readConfiguration(fis);
                String prop = manager.getProperty("org.eclipse.tracecompass.traceeventlogger.SnapshotHandler.maxEvents"); //$NON-NLS-1$
                assertNotNull(prop);
            } catch (IOException e) {
                fail(e.getMessage());
            }
            try {
                new AsyncFileHandler();
            } catch (UnsupportedEncodingException e) {
                assertNotNull(e);
                assertEquals("uTF-6", e.getMessage()); //$NON-NLS-1$
                return;
            }
            fail("should have failed above"); //$NON-NLS-1$
        } catch (FileNotFoundException e) {
            fail(e.getMessage());
        }
    }

    /**
     * Test the getters and setters. They pass everything to the filehandler.
     *
     * @throws SecurityException
     *             should not happen
     * @throws IOException
     *             should not happen
     */
    @Test
    public void testGetterSetters() throws SecurityException, IOException {
        File test = File.createTempFile("test", ".json"); //$NON-NLS-1$ //$NON-NLS-2$
        AsyncFileHandler toTest = new AsyncFileHandler(test.getAbsolutePath());
        toTest.setEncoding("UTF-8"); //$NON-NLS-1$
        assertEquals("UTF-8", toTest.getEncoding()); //$NON-NLS-1$
        Filter f = new Filter() {
            @Override
            public boolean isLoggable(LogRecord record) {
                return false;
            }
        };
        toTest.setFilter(f);
        assertEquals(f, toTest.getFilter());
        toTest.setLevel(Level.CONFIG);
        assertEquals(Level.CONFIG, toTest.getLevel());
        ErrorManager em = new ErrorManager();
        toTest.setErrorManager(em);
        assertEquals(em, toTest.getErrorManager());
        Formatter fmt = new Formatter() {
            @Override
            public String format(LogRecord record) {
                // TODO Auto-generated method stub
                return null;
            }
        };
        toTest.setFormatter(fmt);
        assertEquals(fmt, toTest.getFormatter());
    }

}
