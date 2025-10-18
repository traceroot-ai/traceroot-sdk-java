package ai.traceroot.sdk.logger.logback;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.logger.TraceRootLoggerInterface;
import ai.traceroot.sdk.types.LogLevel;
import ai.traceroot.sdk.utils.LogAppenderUtils;
import ai.traceroot.sdk.utils.ProviderValidationUtils;
import ai.traceroot.sdk.utils.SpanLogCounter;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Logback implementation of TraceRoot logger with automatic trace correlation
 *
 * <p>Usage: LogbackTraceRootLogger logger = LogbackTraceRootLogger.getLogger(MyClass.class);
 * logger.info("This log will include trace correlation");
 */
public class LogbackTraceRootLogger implements TraceRootLoggerInterface {

  private static final Map<String, LogbackTraceRootLogger> loggerInstances =
      new ConcurrentHashMap<>();

  private final Logger logger;

  // Static config holder - all loggers share this config
  private static TraceRootConfigImpl globalConfig;

  private LogbackTraceRootLogger(Logger logger) {
    this.logger = logger;
    // Config is stored globally, not per-instance
  }

  /** Get a TraceRoot logger for the specified class */
  public static LogbackTraceRootLogger getLogger(Class<?> clazz) {
    return getLogger(clazz.getName());
  }

  /** Get a TraceRoot logger for the specified name */
  public static LogbackTraceRootLogger getLogger(String name) {
    return loggerInstances.computeIfAbsent(
        name,
        loggerName -> {
          Logger slf4jLogger = (Logger) LoggerFactory.getLogger(loggerName);
          return new LogbackTraceRootLogger(slf4jLogger);
        });
  }

  /** Initialize TraceRoot logging with configuration */
  public static void initialize(TraceRootConfigImpl config) {
    globalConfig = config;
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    context.putObject("traceRootConfig", config);

    // Set root logger level based on config
    Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
    ch.qos.logback.classic.Level logbackLevel = convertLogLevel(config.getLogLevel());
    rootLogger.setLevel(logbackLevel);

    // Ensure Apache HTTP Client logs are suppressed
    Logger httpClientLogger = context.getLogger("org.apache.hc");
    httpClientLogger.setLevel(ch.qos.logback.classic.Level.WARN);

    // Configure JSON console appender if console logging is enabled
    if (config.isEnableLogConsoleExport()) {
      setupJsonConsoleAppender(context, config);
    }

    // Configure provider-specific cloud appender if cloud logging is enabled
    if (config.isEnableLogCloudExport()) {
      setupProviderCloudAppender(context, config);
    }
  }

  /** Setup JSON console appender programmatically */
  private static void setupJsonConsoleAppender(LoggerContext context, TraceRootConfigImpl config) {
    try {
      Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);

      // Iterate through existing appenders to wrap FILE appenders with TraceRoot format
      java.util.List<ch.qos.logback.core.Appender<ch.qos.logback.classic.spi.ILoggingEvent>>
          appendersToWrap = new java.util.ArrayList<>();
      java.util.Iterator<ch.qos.logback.core.Appender<ch.qos.logback.classic.spi.ILoggingEvent>>
          iter = rootLogger.iteratorForAppenders();
      while (iter.hasNext()) {
        ch.qos.logback.core.Appender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
            iter.next();

        // Wrap FILE appenders to add TraceRoot JSON format
        if ("FILE".equals(appender.getName())
            && !(appender instanceof LogbackFileAppenderWrapper)) {
          appendersToWrap.add(appender);
        }
      }

      // Wrap the FILE appenders
      for (ch.qos.logback.core.Appender<ch.qos.logback.classic.spi.ILoggingEvent> appender :
          appendersToWrap) {
        // Remove the original appender
        rootLogger.detachAppender(appender);

        // Create wrapper with TraceRoot format
        LogbackFileAppenderWrapper wrapper = new LogbackFileAppenderWrapper(appender);
        wrapper.setContext(context);
        wrapper.setConfig(config);
        wrapper.setName("FILE"); // Keep the same name
        wrapper.start();

        // Add the wrapped appender
        rootLogger.addAppender(wrapper);
      }

    } catch (Exception e) {
      System.err.println("[TraceRoot] Failed to wrap file appenders: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /** Setup provider-specific cloud appender programmatically */
  private static void setupProviderCloudAppender(
      LoggerContext context, TraceRootConfigImpl config) {
    try {
      ch.qos.logback.core.Appender<ch.qos.logback.classic.spi.ILoggingEvent> providerAppender =
          LogbackProviderAppenderRegistry.createAppenderForProvider(context, config);

      if (providerAppender != null) {
        // Add appender to root logger
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(providerAppender);
      } else {
        System.err.println(
            "[TraceRoot] Failed to create appender for provider: " + config.getProvider());

        ProviderValidationUtils.validateProviderCredentials(config);
      }
    } catch (Exception e) {
      System.err.println("[TraceRoot] Failed to setup provider cloud appender: " + e.getMessage());
    }
  }

  /** Shutdown TraceRoot logging and clean up resources */
  public static void shutdown() {
    try {
      LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
      TraceRootConfigImpl config = (TraceRootConfigImpl) context.getObject("traceRootConfig");

      // Stop all appenders
      context.stop();

    } catch (Exception e) {
      System.err.println("[TraceRoot] Error shutting down logger: " + e.getMessage());
    }
  }

  /** Convert TraceRoot LogLevel to Logback Level */
  private static ch.qos.logback.classic.Level convertLogLevel(LogLevel logLevel) {
    if (logLevel == null) {
      return ch.qos.logback.classic.Level.DEBUG;
    }

    switch (logLevel) {
      case TRACE:
        return ch.qos.logback.classic.Level.TRACE;
      case DEBUG:
        return ch.qos.logback.classic.Level.DEBUG;
      case INFO:
        return ch.qos.logback.classic.Level.INFO;
      case WARN:
        return ch.qos.logback.classic.Level.WARN;
      case ERROR:
        return ch.qos.logback.classic.Level.ERROR;
      case OFF:
        return ch.qos.logback.classic.Level.OFF;
      default:
        return ch.qos.logback.classic.Level.DEBUG;
    }
  }

  // Logging methods with automatic trace correlation

  @Override
  public void trace(String message) {
    SpanLogCounter.incrementTraceLogCount();
    logWithTraceCorrelation(() -> logger.trace(message));
  }

  @Override
  public void trace(String format, Object... args) {
    SpanLogCounter.incrementTraceLogCount();
    logWithTraceCorrelation(() -> logger.trace(format, args));
  }

  @Override
  public void trace(String message, Throwable throwable) {
    SpanLogCounter.incrementTraceLogCount();
    logWithTraceCorrelation(() -> logger.trace(message, throwable));
  }

  @Override
  public void debug(String message) {
    SpanLogCounter.incrementDebugLogCount();
    logWithTraceCorrelation(() -> logger.debug(message));
  }

  @Override
  public void debug(String format, Object... args) {
    SpanLogCounter.incrementDebugLogCount();
    logWithTraceCorrelation(() -> logger.debug(format, args));
  }

  @Override
  public void debug(String message, Throwable throwable) {
    SpanLogCounter.incrementDebugLogCount();
    logWithTraceCorrelation(() -> logger.debug(message, throwable));
  }

  @Override
  public void info(String message) {
    SpanLogCounter.incrementInfoLogCount();
    logWithTraceCorrelation(() -> logger.info(message));
  }

  @Override
  public void info(String format, Object... args) {
    SpanLogCounter.incrementInfoLogCount();
    logWithTraceCorrelation(() -> logger.info(format, args));
  }

  @Override
  public void info(String message, Throwable throwable) {
    SpanLogCounter.incrementInfoLogCount();
    logWithTraceCorrelation(() -> logger.info(message, throwable));
  }

  @Override
  public void warn(String message) {
    SpanLogCounter.incrementWarnLogCount();
    logWithTraceCorrelation(() -> logger.warn(message));
  }

  @Override
  public void warn(String format, Object... args) {
    SpanLogCounter.incrementWarnLogCount();
    logWithTraceCorrelation(() -> logger.warn(format, args));
  }

  @Override
  public void warn(String message, Throwable throwable) {
    SpanLogCounter.incrementWarnLogCount();
    logWithTraceCorrelation(() -> logger.warn(message, throwable));
  }

  @Override
  public void error(String message) {
    SpanLogCounter.incrementErrorLogCount();
    logWithTraceCorrelation(() -> logger.error(message));
  }

  @Override
  public void error(String format, Object... args) {
    SpanLogCounter.incrementErrorLogCount();
    logWithTraceCorrelation(() -> logger.error(format, args));
  }

  @Override
  public void error(String message, Throwable throwable) {
    SpanLogCounter.incrementErrorLogCount();
    logWithTraceCorrelation(() -> logger.error(message, throwable));
  }

  /** Log with additional context attributes */
  public void logWithContext(LogLevel level, String message, Map<String, String> context) {
    // Add context to MDC
    if (context != null) {
      context.forEach(MDC::put);
    }

    try {
      switch (level) {
        case DEBUG:
          debug(message);
          break;
        case INFO:
          info(message);
          break;
        case WARN:
          warn(message);
          break;
        case ERROR:
          error(message);
          break;
        default:
          info(message);
          break;
      }
    } finally {
      // Clean up MDC
      if (context != null) {
        context.keySet().forEach(MDC::remove);
      }
    }
  }

  /** Add log event to current span (simplified - local mode not implemented) */
  public void addSpanEventDirectly(String eventName, Map<String, String> attributes) {
    if (globalConfig != null && globalConfig.isLocalMode()) {
      // Local mode: just log the event
      logger.debug("[TraceRoot Local] Span event: {} with attributes: {}", eventName, attributes);
    }
    // Note: Complex span event implementation removed for simplicity
  }

  /** Wrapper method to add trace correlation to MDC before logging */
  private void logWithTraceCorrelation(Runnable logAction) {
    if (globalConfig != null && globalConfig.isLocalMode()) {
      // Local mode: just print info and proceed with normal logging
      System.out.println("[TraceRoot Local] Logging in local mode");
    }

    /*
     * MEMORY SAFETY: MDC Thread-Local Management
     *
     * MDC (Mapped Diagnostic Context) uses ThreadLocal storage. In high-throughput applications,
     * improper MDC management can cause memory leaks because:
     * 1. ThreadLocal values persist for the lifetime of the thread
     * 2. In thread pools (common in Spring Boot), threads are reused
     * 3. Unreleased ThreadLocal values accumulate over time
     *
     * OUR SOLUTION:
     * - Always capture original MDC state before modification
     * - Use try-finally to guarantee cleanup regardless of exceptions
     * - Restore original state or explicitly remove keys to prevent accumulation
     * - This ensures zero memory leaks even in high-throughput scenarios
     */

    // Capture stack trace at log creation time for async/batch processing
    // MEMORY SAFETY: Store original MDC state for proper cleanup
    String originalStackTrace = MDC.get("traceroot.stack_trace");
    try {
      StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
      StackTraceElement caller = LogAppenderUtils.findUserLoggingLocation(stackTrace);

      if (caller != null) {
        String methodName = LogAppenderUtils.cleanAspectJMethodName(caller.getMethodName());
        int lineNumber = caller.getLineNumber();

        String filePath = LogAppenderUtils.getFilePath(caller);

        // Apply root path transformation if configured
        if (globalConfig != null
            && globalConfig.getRootPath() != null
            && filePath.startsWith(globalConfig.getRootPath())) {
          filePath = filePath.substring(globalConfig.getRootPath().length());
          if (filePath.startsWith("/")) {
            filePath = filePath.substring(1);
          }
        }

        String stackTraceInfo = String.format("%s:%s:%d", filePath, methodName, lineNumber);
        // MEMORY SAFETY: MDC value will be cleaned up in finally block
        MDC.put("traceroot.stack_trace", stackTraceInfo);
      }

      // The trace correlation will be automatically handled by TraceCorrelationConverter
      // in the Logback configuration
      logAction.run();
    } finally {
      /*
       * CRITICAL MEMORY SAFETY: MDC Cleanup
       *
       * This finally block is essential for preventing memory leaks:
       * - Restores original MDC state if there was one
       * - Removes our key completely if no original value existed
       * - Executes even if exceptions occur during logging
       * - Ensures ThreadLocal storage doesn't accumulate stale values
       *
       * WITHOUT THIS CLEANUP: In high-throughput Spring Boot applications,
       * ThreadLocal values would accumulate indefinitely, causing OOM errors.
       */
      if (originalStackTrace != null) {
        MDC.put("traceroot.stack_trace", originalStackTrace);
      } else {
        MDC.remove("traceroot.stack_trace");
      }
    }
  }

  // Delegate methods for compatibility

  @Override
  public boolean isTraceEnabled() {
    return logger.isTraceEnabled();
  }

  @Override
  public boolean isDebugEnabled() {
    return logger.isDebugEnabled();
  }

  @Override
  public boolean isInfoEnabled() {
    return logger.isInfoEnabled();
  }

  @Override
  public boolean isWarnEnabled() {
    return logger.isWarnEnabled();
  }

  @Override
  public boolean isErrorEnabled() {
    return logger.isErrorEnabled();
  }

  @Override
  public String getName() {
    return logger.getName();
  }

  /** Get the underlying SLF4J logger */
  public Logger getUnderlyingLogger() {
    return logger;
  }
}
