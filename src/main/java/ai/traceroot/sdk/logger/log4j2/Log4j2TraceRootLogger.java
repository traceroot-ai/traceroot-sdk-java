package ai.traceroot.sdk.logger.log4j2;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.types.LogLevel;
import ai.traceroot.sdk.utils.LogAppenderUtils;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;

/**
 * Log4j2 logger wrapper for TraceRoot SDK with automatic trace correlation
 *
 * <p>Usage: Log4j2TraceRootLogger logger = Log4j2TraceRootLogger.getLogger(MyClass.class);
 * logger.info("This log will include trace correlation");
 */
public class Log4j2TraceRootLogger {

  private final Logger logger;
  private TraceRootConfigImpl config;

  // Static config holder
  private static TraceRootConfigImpl globalConfig;

  private Log4j2TraceRootLogger(Logger logger, TraceRootConfigImpl config) {
    this.logger = logger;
    this.config = config;
  }

  /** Get a TraceRoot logger for the specified class */
  public static Log4j2TraceRootLogger getLogger(Class<?> clazz) {
    return getLogger(clazz.getName());
  }

  /** Get a TraceRoot logger for the specified name */
  public static Log4j2TraceRootLogger getLogger(String name) {
    Logger log4j2Logger = LogManager.getLogger(name);
    return new Log4j2TraceRootLogger(log4j2Logger, globalConfig);
  }

  /** Initialize TraceRoot logging with configuration */
  public static void initialize(TraceRootConfigImpl config) {
    globalConfig = config;
    LoggerContext context = (LoggerContext) LogManager.getContext(false);

    // Set root logger level based on config
    org.apache.logging.log4j.Level log4j2Level = convertLogLevel(config.getLogLevel());
    Configurator.setRootLevel(log4j2Level);

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
      Log4j2JsonConsoleAppender jsonConsoleAppender =
          Log4j2JsonConsoleAppender.createAppender("JsonConsoleAppender", null, null, true);
      if (jsonConsoleAppender != null) {
        jsonConsoleAppender.setConfig(config);
        jsonConsoleAppender.start();

        // Get root logger
        org.apache.logging.log4j.core.Logger rootLogger =
            context.getLogger(LogManager.ROOT_LOGGER_NAME);

        // Remove existing console appender to avoid duplicate logs
        org.apache.logging.log4j.core.Appender consoleAppender =
            rootLogger.getAppenders().get("CONSOLE");
        if (consoleAppender != null) {
          rootLogger.removeAppender(consoleAppender);
        }

        // Add our custom console appender
        rootLogger.addAppender(jsonConsoleAppender);
      }
    } catch (Exception e) {
      System.err.println("[TraceRoot] Failed to setup JSON console appender: " + e.getMessage());
    }
  }

  /** Setup provider-specific cloud appender programmatically */
  private static void setupProviderCloudAppender(
      LoggerContext context, TraceRootConfigImpl config) {
    try {
      org.apache.logging.log4j.core.Appender providerAppender =
          Log4j2ProviderAppenderRegistry.createAppenderForProvider(context, config);

      if (providerAppender != null) {
        org.apache.logging.log4j.core.Logger rootLogger =
            context.getLogger(LogManager.ROOT_LOGGER_NAME);
        rootLogger.addAppender(providerAppender);
      } else {
        System.err.println(
            "[TraceRoot] Failed to create appender for provider: " + config.getProvider());
      }
    } catch (Exception e) {
      System.err.println("[TraceRoot] Failed to setup provider cloud appender: " + e.getMessage());
    }
  }

  /** Shutdown TraceRoot logging and clean up resources */
  public static void shutdown() {
    try {
      // CRITICAL: Wait before stopping context to allow async CLS client to send logs
      // The Tencent CLS SDK batches logs and sends them asynchronously with a 2-second linger time
      Thread.sleep(4000);

      LoggerContext context = (LoggerContext) LogManager.getContext(false);
      context.stop();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.err.println("[TraceRoot] Interrupted during shutdown wait");
    } catch (Exception e) {
      System.err.println("[TraceRoot] Error shutting down logger: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /** Convert TraceRoot LogLevel to Log4j2 Level */
  private static org.apache.logging.log4j.Level convertLogLevel(LogLevel logLevel) {
    if (logLevel == null) {
      return org.apache.logging.log4j.Level.DEBUG;
    }

    switch (logLevel) {
      case DEBUG:
        return org.apache.logging.log4j.Level.DEBUG;
      case INFO:
        return org.apache.logging.log4j.Level.INFO;
      case WARN:
        return org.apache.logging.log4j.Level.WARN;
      case ERROR:
        return org.apache.logging.log4j.Level.ERROR;
      case OFF:
        return org.apache.logging.log4j.Level.OFF;
      default:
        return org.apache.logging.log4j.Level.DEBUG;
    }
  }

  // Logging methods with automatic trace correlation

  public void debug(String message) {
    logWithTraceCorrelation(() -> logger.debug(message));
  }

  public void debug(String format, Object... args) {
    logWithTraceCorrelation(() -> logger.debug(format, args));
  }

  public void info(String message) {
    logWithTraceCorrelation(() -> logger.info(message));
  }

  public void info(String format, Object... args) {
    logWithTraceCorrelation(() -> logger.info(format, args));
  }

  public void warn(String message) {
    logWithTraceCorrelation(() -> logger.warn(message));
  }

  public void warn(String format, Object... args) {
    logWithTraceCorrelation(() -> logger.warn(format, args));
  }

  public void warn(String message, Throwable throwable) {
    logWithTraceCorrelation(() -> logger.warn(message, throwable));
  }

  public void error(String message) {
    logWithTraceCorrelation(() -> logger.error(message));
  }

  public void error(String format, Object... args) {
    logWithTraceCorrelation(() -> logger.error(format, args));
  }

  public void error(String message, Throwable throwable) {
    logWithTraceCorrelation(() -> logger.error(message, throwable));
  }

  /** Log with additional context attributes */
  public void logWithContext(LogLevel level, String message, Map<String, String> context) {
    // Add context to ThreadContext
    if (context != null) {
      context.forEach(ThreadContext::put);
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
      // Clean up ThreadContext
      if (context != null) {
        context.keySet().forEach(ThreadContext::remove);
      }
    }
  }

  /** Wrapper method to add trace correlation to ThreadContext before logging */
  private void logWithTraceCorrelation(Runnable logAction) {
    if (config != null && config.isLocalMode()) {
      // Local mode: just print info and proceed with normal logging
      System.out.println("[TraceRoot Local] Logging in local mode");
    }

    // Capture stack trace at log creation time
    String originalStackTrace = ThreadContext.get("traceroot.stack_trace");
    try {
      StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
      StackTraceElement caller = LogAppenderUtils.findUserLoggingLocation(stackTrace);

      if (caller != null) {
        String methodName = LogAppenderUtils.cleanAspectJMethodName(caller.getMethodName());
        int lineNumber = caller.getLineNumber();

        String filePath = LogAppenderUtils.getFilePath(caller);

        // Apply root path transformation if configured
        if (config != null
            && config.getRootPath() != null
            && filePath.startsWith(config.getRootPath())) {
          filePath = filePath.substring(config.getRootPath().length());
          if (filePath.startsWith("/")) {
            filePath = filePath.substring(1);
          }
        }

        String stackTraceInfo = String.format("%s:%s:%d", filePath, methodName, lineNumber);
        ThreadContext.put("traceroot.stack_trace", stackTraceInfo);
      }

      // Execute the logging action
      logAction.run();
    } finally {
      // Clean up ThreadContext
      if (originalStackTrace != null) {
        ThreadContext.put("traceroot.stack_trace", originalStackTrace);
      } else {
        ThreadContext.remove("traceroot.stack_trace");
      }
    }
  }

  // Delegate methods for compatibility

  public boolean isDebugEnabled() {
    return logger.isDebugEnabled();
  }

  public boolean isInfoEnabled() {
    return logger.isInfoEnabled();
  }

  public boolean isWarnEnabled() {
    return logger.isWarnEnabled();
  }

  public boolean isErrorEnabled() {
    return logger.isErrorEnabled();
  }

  public String getName() {
    return logger.getName();
  }

  /** Get the underlying Log4j2 logger */
  public Logger getUnderlyingLogger() {
    return logger;
  }
}
