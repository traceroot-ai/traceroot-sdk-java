package ai.traceroot.sdk.logger;

import ai.traceroot.sdk.types.LogLevel;
import java.util.Map;

/**
 * Unified logger factory that automatically detects and delegates to the appropriate logging
 * backend
 *
 * <p>This factory automatically detects whether Logback or Log4j2 is available on the classpath and
 * returns the appropriate logger implementation. Prefers Logback if both are available.
 *
 * <p>Usage: TraceRootLogger logger = TraceRootLogger.getLogger(MyClass.class); logger.info("This
 * works with both Logback and Log4j2!");
 */
public class TraceRootLogger implements TraceRootLoggerInterface {

  private final TraceRootLoggerInterface delegate;
  private static final LoggerBackend detectedBackend = detectBackend();

  private TraceRootLogger(TraceRootLoggerInterface delegate) {
    this.delegate = delegate;
  }

  /** Get a TraceRoot logger for the specified class */
  public static TraceRootLogger getLogger(Class<?> clazz) {
    return getLogger(clazz.getName());
  }

  /** Get a TraceRoot logger for the specified name */
  public static TraceRootLogger getLogger(String name) {
    TraceRootLoggerInterface implementation = createLoggerImplementation(name);
    return new TraceRootLogger(implementation);
  }

  /** Create the appropriate logger implementation based on detected backend */
  private static TraceRootLoggerInterface createLoggerImplementation(String name) {
    switch (detectedBackend) {
      case LOGBACK:
        try {
          Class<?> logbackLoggerClass =
              Class.forName("ai.traceroot.sdk.logger.logback.LogbackTraceRootLogger");
          java.lang.reflect.Method getLoggerMethod =
              logbackLoggerClass.getMethod("getLogger", String.class);
          return (TraceRootLoggerInterface) getLoggerMethod.invoke(null, name);
        } catch (Exception e) {
          throw new RuntimeException("Failed to create Logback logger", e);
        }

      case LOG4J2:
        try {
          Class<?> log4j2LoggerClass =
              Class.forName("ai.traceroot.sdk.logger.log4j2.Log4j2TraceRootLogger");
          java.lang.reflect.Method getLoggerMethod =
              log4j2LoggerClass.getMethod("getLogger", String.class);
          return (TraceRootLoggerInterface) getLoggerMethod.invoke(null, name);
        } catch (Exception e) {
          throw new RuntimeException("Failed to create Log4j2 logger", e);
        }

      case NONE:
      default:
        throw new IllegalStateException(
            "No supported logging backend found. Please add either Logback or Log4j2 to your"
                + " classpath.");
    }
  }

  /** Detect which logging backend is available on the classpath (prefers Logback) */
  private static LoggerBackend detectBackend() {
    // Try Logback first (preferred)
    if (isLogbackAvailable()) {
      return LoggerBackend.LOGBACK;
    }

    // Fall back to Log4j2
    if (isLog4j2Available()) {
      return LoggerBackend.LOG4J2;
    }

    // No supported backend found
    return LoggerBackend.NONE;
  }

  /** Check if Logback is available on the classpath */
  private static boolean isLogbackAvailable() {
    try {
      Class.forName("ch.qos.logback.classic.Logger");
      Class.forName("ai.traceroot.sdk.logger.logback.LogbackTraceRootLogger");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  /** Check if Log4j2 is available on the classpath */
  private static boolean isLog4j2Available() {
    try {
      Class.forName("org.apache.logging.log4j.core.Logger");
      Class.forName("ai.traceroot.sdk.logger.log4j2.Log4j2TraceRootLogger");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  /** Get the detected logging backend */
  public static LoggerBackend getDetectedBackend() {
    return detectedBackend;
  }

  // Delegate all methods to the implementation

  @Override
  public void debug(String message) {
    delegate.debug(message);
  }

  @Override
  public void debug(String format, Object... args) {
    delegate.debug(format, args);
  }

  @Override
  public void info(String message) {
    delegate.info(message);
  }

  @Override
  public void info(String format, Object... args) {
    delegate.info(format, args);
  }

  @Override
  public void warn(String message) {
    delegate.warn(message);
  }

  @Override
  public void warn(String format, Object... args) {
    delegate.warn(format, args);
  }

  @Override
  public void warn(String message, Throwable throwable) {
    delegate.warn(message, throwable);
  }

  @Override
  public void error(String message) {
    delegate.error(message);
  }

  @Override
  public void error(String format, Object... args) {
    delegate.error(format, args);
  }

  @Override
  public void error(String message, Throwable throwable) {
    delegate.error(message, throwable);
  }

  @Override
  public void logWithContext(LogLevel level, String message, Map<String, String> context) {
    delegate.logWithContext(level, message, context);
  }

  @Override
  public boolean isDebugEnabled() {
    return delegate.isDebugEnabled();
  }

  @Override
  public boolean isInfoEnabled() {
    return delegate.isInfoEnabled();
  }

  @Override
  public boolean isWarnEnabled() {
    return delegate.isWarnEnabled();
  }

  @Override
  public boolean isErrorEnabled() {
    return delegate.isErrorEnabled();
  }

  @Override
  public String getName() {
    return delegate.getName();
  }

  /** Enum representing available logging backends */
  public enum LoggerBackend {
    LOGBACK,
    LOG4J2,
    NONE
  }
}
