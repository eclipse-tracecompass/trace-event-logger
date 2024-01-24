package org.eclipse.tracecompass.trace_event_logger;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Test;

public class TestLoggerBenchmark {

	private Logger fLogger;
	private Handler oldFileHandler;
	private Handler newFileHandler;
	private File[] files = new File[2];

	@Test
	public void testBench() throws SecurityException, IOException {
		long warmUp = 2000;
		long maxRuns = warmUp * 10;
		fLogger = Logger.getAnonymousLogger();
		files[0] = new File("/tmp/trace-old.json");
		files[1] = new File("/tmp/trace-new.json");
		oldFileHandler = new FileHandler(files[0].getAbsolutePath());
		oldFileHandler.setFormatter(new Formatter() {
			@Override
			public String format(LogRecord record) {
				return record.getMessage() + ",\n";
			}
		});
		oldFileHandler.setLevel(Level.ALL);
		fLogger.addHandler(oldFileHandler);
		fLogger.setLevel(Level.ALL);
		Logger logger = fLogger;
		List<Long> asyncNew = new ArrayList<>();
		List<Long> asyncOld = new ArrayList<>();
		List<Long> syncNew = new ArrayList<>();
		List<Long> syncOld = new ArrayList<>();
		List<Long> run = new ArrayList<>();
		for (long runs = 2000; runs < maxRuns; runs *= 1.4) {
			for (long i = 0; i < warmUp; i++) {
				try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.FINE, "foo")) {
					try (LogUtils.ScopeLog log1 = new LogUtils.ScopeLog(logger, Level.FINER, "bar")) {
						try (LogUtils.ScopeLog log2 = new LogUtils.ScopeLog(logger, Level.FINEST, "baz")) {
							// do something
							new Object();
						}
					}
				}
			}

			long start = System.nanoTime();
			for (long i = 0; i < runs; i++) {
				try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.FINE, "foo", "run", runs, "test", i)) {
					try (LogUtils.ScopeLog log1 = new LogUtils.ScopeLog(logger, Level.FINER, "bar", "run", runs, "test",
							i)) {
						try (LogUtils.ScopeLog log2 = new LogUtils.ScopeLog(logger, Level.FINEST, "baz", "run", runs,
								"test", i)) {
							// do something
							new Object();
						}
					}
				}
			}
			long end = System.nanoTime();
			syncNew.add(end - start);
			for (long i = 0; i < warmUp; i++) {
				try (OldLogUtils.ScopeLog log = new OldLogUtils.ScopeLog(logger, Level.FINE, "foo", "run", runs, "test",
						i)) {
					try (OldLogUtils.ScopeLog log1 = new OldLogUtils.ScopeLog(logger, Level.FINER, "bar", "run", runs,
							"test", i)) {
						try (OldLogUtils.ScopeLog log2 = new OldLogUtils.ScopeLog(logger, Level.FINEST, "baz", "run",
								runs, "test", i)) {
							// do something
							new Object();
						}
					}
				}
			}

			long start2 = System.nanoTime();
			for (long i = 0; i < runs; i++) {
				try (OldLogUtils.ScopeLog log = new OldLogUtils.ScopeLog(logger, Level.FINE, "foo", "run", runs, "test",
						i)) {
					try (OldLogUtils.ScopeLog log1 = new OldLogUtils.ScopeLog(logger, Level.FINER, "bar", "run", runs,
							"test", i)) {
						try (OldLogUtils.ScopeLog log2 = new OldLogUtils.ScopeLog(logger, Level.FINEST, "baz", "run",
								runs, "test", i)) {
							// do something
							new Object();
						}
					}
				}
			}
			long end2 = System.nanoTime();
			syncOld.add(end2 - start2);
			run.add(runs);
		}
		fLogger.removeHandler(oldFileHandler);
		newFileHandler = new AsyncFileHandler(files[1].getAbsolutePath());
		newFileHandler.setFormatter(new Formatter() {
			@Override
			public String format(LogRecord record) {
				return record.getMessage() + ",\n";
			}
		});
		newFileHandler.setLevel(Level.ALL);
		fLogger.addHandler(newFileHandler);
		for (long runs = 2000; runs < maxRuns; runs *= 1.4) {
			for (long i = 0; i < warmUp; i++) {
				try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.FINE, "foo")) {
					try (LogUtils.ScopeLog log1 = new LogUtils.ScopeLog(logger, Level.FINER, "bar")) {
						try (LogUtils.ScopeLog log2 = new LogUtils.ScopeLog(logger, Level.FINEST, "baz")) {
							// do something
							new Object();
						}
					}
				}
			}

			long start = System.nanoTime();
			for (long i = 0; i < runs; i++) {
				try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.FINE, "foo", "run", runs, "test", i)) {
					try (LogUtils.ScopeLog log1 = new LogUtils.ScopeLog(logger, Level.FINER, "bar", "run", runs, "test",
							i)) {
						try (LogUtils.ScopeLog log2 = new LogUtils.ScopeLog(logger, Level.FINEST, "baz", "run", runs,
								"test", i)) {
							// do something
							new Object();
						}
					}
				}
			}
			long end = System.nanoTime();
			asyncNew.add(end - start);
			for (long i = 0; i < warmUp; i++) {
				try (OldLogUtils.ScopeLog log = new OldLogUtils.ScopeLog(logger, Level.FINE, "foo")) {
					try (OldLogUtils.ScopeLog log1 = new OldLogUtils.ScopeLog(logger, Level.FINER, "bar")) {
						try (OldLogUtils.ScopeLog log2 = new OldLogUtils.ScopeLog(logger, Level.FINEST, "baz")) {
							// do something
							new Object();
						}
					}
				}
			}

			long start2 = System.nanoTime();
			for (long i = 0; i < runs; i++) {
				try (OldLogUtils.ScopeLog log = new OldLogUtils.ScopeLog(logger, Level.FINE, "foo", "run", runs, "test",
						i)) {
					try (OldLogUtils.ScopeLog log1 = new OldLogUtils.ScopeLog(logger, Level.FINER, "bar", "run", runs,
							"test", i)) {
						try (OldLogUtils.ScopeLog log2 = new OldLogUtils.ScopeLog(logger, Level.FINEST, "baz", "run",
								runs, "test", i)) {
							// do something
							new Object();
						}
					}
				}
			}
			long end2 = System.nanoTime();
			asyncOld.add(end2 - start2);
		}
		System.out.println("Runs,SyncOld,SyncNew,AsyncOld,AsyncNew");
		for (int i = 0; i < run.size(); i++) {
			System.out.println(String.format("%d,%d,%d,%d,%d", run.get(i), syncOld.get(i), syncNew.get(i),
					asyncOld.get(i), asyncNew.get(i)));
		}
	}

	private static long linecount(Path path) throws IOException {
		long linecount = 0;
		try (Stream<String> stream = Files.lines(path, StandardCharsets.UTF_8)) {
			linecount = stream.count();
		}
		return linecount;
	}

	@After
	public void waiting() {
		try {
			while (linecount(files[0].toPath()) != linecount(files[1].toPath())) {
				if(oldFileHandler != null) {
					oldFileHandler.close();
				}
				if(newFileHandler != null) {
					newFileHandler.close();
				}
				Thread.currentThread().sleep(100);
			}
		} catch (IOException | InterruptedException e) {
			fail(e.toString());
		}
	}

	@Test
	public void testLeanBench() throws SecurityException, IOException {
		long warmUp = 2000;
		long maxRuns = warmUp * 10;
		fLogger = Logger.getAnonymousLogger();
		files[0] = new File("/tmp/trace-lean-old.json");
		files[1] = new File("/tmp/trace-lean-new.json");
		oldFileHandler = new FileHandler(files[0].getAbsolutePath());
		oldFileHandler.setFormatter(new Formatter() {
			@Override
			public String format(LogRecord record) {
				return record.getMessage() + ",\n";
			}
		});
		oldFileHandler.setLevel(Level.ALL);
		fLogger.addHandler(oldFileHandler);
		fLogger.setLevel(Level.ALL);
		Logger logger = fLogger;
		List<Long> asyncNew = new ArrayList<>();
		List<Long> asyncOld = new ArrayList<>();
		List<Long> syncNew = new ArrayList<>();
		List<Long> syncOld = new ArrayList<>();
		List<Long> run = new ArrayList<>();
		for (long runs = 2000; runs < maxRuns; runs *= 1.4) {
			for (long i = 0; i < warmUp; i++) {
				try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.FINE, "foo")) {
					try (LogUtils.ScopeLog log1 = new LogUtils.ScopeLog(logger, Level.FINER, "bar")) {
						try (LogUtils.ScopeLog log2 = new LogUtils.ScopeLog(logger, Level.FINEST, "baz")) {
							// do something
							new Object();
						}
					}
				}
			}

			long start = System.nanoTime();
			for (long i = 0; i < runs; i++) {
				try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.FINE, "foo")) {
					try (LogUtils.ScopeLog log1 = new LogUtils.ScopeLog(logger, Level.FINER, "bar")) {
						try (LogUtils.ScopeLog log2 = new LogUtils.ScopeLog(logger, Level.FINEST, "baz")) {
							// do something
							new Object();
						}
					}
				}
			}
			long end = System.nanoTime();
			syncNew.add(end - start);
			for (long i = 0; i < warmUp; i++) {
				try (OldLogUtils.ScopeLog log = new OldLogUtils.ScopeLog(logger, Level.FINE, "foo")) {
					try (OldLogUtils.ScopeLog log1 = new OldLogUtils.ScopeLog(logger, Level.FINER, "bar")) {
						try (OldLogUtils.ScopeLog log2 = new OldLogUtils.ScopeLog(logger, Level.FINEST, "baz")) {
							// do something
							new Object();
						}
					}
				}
			}

			long start2 = System.nanoTime();
			for (long i = 0; i < runs; i++) {
				try (OldLogUtils.ScopeLog log = new OldLogUtils.ScopeLog(logger, Level.FINE, "foo")) {
					try (OldLogUtils.ScopeLog log1 = new OldLogUtils.ScopeLog(logger, Level.FINER, "bar")) {
						try (OldLogUtils.ScopeLog log2 = new OldLogUtils.ScopeLog(logger, Level.FINEST, "baz")) {
							// do something
							new Object();
						}
					}
				}
			}
			long end2 = System.nanoTime();
			syncOld.add(end2 - start2);
			run.add(runs);
		}
		fLogger.removeHandler(oldFileHandler);
		newFileHandler = new AsyncFileHandler(files[1].getAbsolutePath());
		newFileHandler.setFormatter(new Formatter() {
			@Override
			public String format(LogRecord record) {
				return record.getMessage() + ",\n";
			}
		});
		newFileHandler.setLevel(Level.ALL);
		fLogger.addHandler(newFileHandler);
		for (long runs = 2000; runs < maxRuns; runs *= 1.4) {
			for (long i = 0; i < warmUp; i++) {
				try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.FINE, "foo")) {
					try (LogUtils.ScopeLog log1 = new LogUtils.ScopeLog(logger, Level.FINER, "bar")) {
						try (LogUtils.ScopeLog log2 = new LogUtils.ScopeLog(logger, Level.FINEST, "baz")) {
							// do something
							new Object();
						}
					}
				}
			}

			long start = System.nanoTime();
			for (long i = 0; i < runs; i++) {
				try (LogUtils.ScopeLog log = new LogUtils.ScopeLog(logger, Level.FINE, "foo")) {
					try (LogUtils.ScopeLog log1 = new LogUtils.ScopeLog(logger, Level.FINER, "bar")) {
						try (LogUtils.ScopeLog log2 = new LogUtils.ScopeLog(logger, Level.FINEST, "baz", "run", runs,
								"test", i)) {
							// do something
							new Object();
						}
					}
				}
			}
			long end = System.nanoTime();
			asyncNew.add(end - start);
			for (long i = 0; i < warmUp; i++) {
				try (OldLogUtils.ScopeLog log = new OldLogUtils.ScopeLog(logger, Level.FINE, "foo")) {
					try (OldLogUtils.ScopeLog log1 = new OldLogUtils.ScopeLog(logger, Level.FINER, "bar")) {
						try (OldLogUtils.ScopeLog log2 = new OldLogUtils.ScopeLog(logger, Level.FINEST, "baz")) {
							// do something
							new Object();
						}
					}
				}
			}

			long start2 = System.nanoTime();
			for (long i = 0; i < runs; i++) {
				try (OldLogUtils.ScopeLog log = new OldLogUtils.ScopeLog(logger, Level.FINE, "foo", "run", runs, "test",
						i)) {
					try (OldLogUtils.ScopeLog log1 = new OldLogUtils.ScopeLog(logger, Level.FINER, "bar")) {
						try (OldLogUtils.ScopeLog log2 = new OldLogUtils.ScopeLog(logger, Level.FINEST, "baz")) {
							// do something
							new Object();
						}
					}
				}
			}
			long end2 = System.nanoTime();
			asyncOld.add(end2 - start2);
		}
		System.out.println("Runs,SyncOldLean,SyncNewLean,AsyncOldLean,AsyncNewLean");
		for (int i = 0; i < run.size(); i++) {
			System.out.println(String.format("%d,%d,%d,%d,%d", run.get(i), syncOld.get(i), syncNew.get(i),
					asyncOld.get(i), asyncNew.get(i)));
		}
	}
}