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
package org.eclipse.tracecompass.traceeventlogger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test cases for logger with AsyncFileHandler(line sensitive!)
 *
 * @author Bernd Hufmann
 */
public class LoggerWithHandlerTest {

    private Logger fLogger;
    private StreamHandler fStreamHandler;
    private File fTempFile;
    private String originalFormat;

    /**
     * Set up logger
     */
    @Before
    public void before() {
        try {
            originalFormat = System.getProperty("java.util.logging.SimpleFormatter.format"); //$NON-NLS-1$
            System.setProperty("java.util.logging.SimpleFormatter.format", "%5$s%n"); //$NON-NLS-1$ //$NON-NLS-2$
            LogManager.getLogManager().reset();
            fLogger = Logger.getAnonymousLogger();
            fTempFile = File.createTempFile("test", ".json"); //$NON-NLS-1$ //$NON-NLS-2$
            fStreamHandler = new AsyncFileHandler(fTempFile.getAbsolutePath());
            fStreamHandler.setLevel(Level.ALL);
            fStreamHandler.setEncoding("UTF-8"); //$NON-NLS-1$
            fStreamHandler.setFormatter(new SimpleFormatter());
            fLogger.addHandler(fStreamHandler);

        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    /**
     * Cleanup after test execution
     */
    @After
    public void after() {
        if (originalFormat != null) {
            System.setProperty("java.util.logging.SimpleFormatter.format", originalFormat); //$NON-NLS-1$
        } else {
            System.clearProperty("java.util.logging.SimpleFormatter.format"); //$NON-NLS-1$
        }
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
        logger.log(Level.INFO, "Should not be logged"); //$NON-NLS-1$

        fStreamHandler.flush();

        List<String> filledLines = Collections.emptyList();
        // Give writing thread time to write all events
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ex) {
            // ignore
        }
        try {
            for (int i = 0; i < 10; i++) {
                List<String> allLines = Files.readAllLines(Paths.get(fTempFile.getAbsolutePath()));
                filledLines = allLines.stream().filter(Objects::nonNull).collect(Collectors.toList());
                if (filledLines.size() >= 2) {
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    // ignore
                }
            }
            assertNotNull(filledLines);
            assertEquals(2, filledLines.size());
            Pattern pattern = Pattern.compile("\\{.*B.*world.*\\}"); //$NON-NLS-1$
            assertTrue(pattern.matcher(filledLines.get(0)).matches());
            pattern = Pattern.compile("\\{.*E.*\\}"); //$NON-NLS-1$
            assertTrue(pattern.matcher(filledLines.get(1)).matches());
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }
}
