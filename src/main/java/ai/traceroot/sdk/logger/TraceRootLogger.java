package ai.traceroot.sdk.logger;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.types.LogLevel;
import ai.traceroot.sdk.utils.ProviderValidationUtils;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Main logger class for TraceRoot SDK with automatic trace correlation
 *
 * <p>Usage: TraceRootLogger logger = TraceRootLogger.getLogger(MyClass.class); logger.info("This
 * log will include trace correlation");
 */
public class TraceRootLogger {

  private static final Map<String, TraceRootLogger> loggerInstances = new ConcurrentHashMap<>();

  private final Logger logger;
  private TraceRootConfigImpl config;

  private TraceRootLogger(Logger logger, TraceRootConfigImpl config) {
    this.logger = logger;
    this.config = config;
  }

  /** Get a TraceRoot logger for the specified class */
  public static TraceRootLogger getLogger(Class<?> clazz) {
    return getLogger(clazz.getName());
  }

  /** Get a TraceRoot logger for the specified name */
  public static TraceRootLogger getLogger(String name) {
    return loggerInstances.computeIfAbsent(
        name,
        loggerName -> {
          Logger slf4jLogger = (Logger) LoggerFactory.getLogger(loggerName);

          // Try to get config from logger context
          LoggerContext context = slf4jLogger.getLoggerContext();
          TraceRootConfigImpl config = (TraceRootConfigImpl) context.getObject("traceRootConfig");

          return new TraceRootLogger(slf4jLogger, config);
        });
  }

  /** Initialize TraceRoot logging with configuration */
  public static void initialize(TraceRootConfigImpl config) {
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
      JsonConsoleAppender jsonConsoleAppender = new JsonConsoleAppender();
      jsonConsoleAppender.setContext(context);
      jsonConsoleAppender.setConfig(config);
      jsonConsoleAppender.setName("JsonConsoleAppender");

      // Set the target to System.out (required for ConsoleAppender to start)
      jsonConsoleAppender.setTarget("System.out");

      jsonConsoleAppender.start();

      // Remove existing console appender and add JSON console appender
      Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);

      // Remove all existing appenders
      rootLogger.detachAndStopAllAppenders();

      rootLogger.addAppender(jsonConsoleAppender);

    } catch (Exception e) {
      System.err.println("[TraceRoot] Failed to setup JSON console appender: " + e.getMessage());
      e.printStackTrace();
    }

    // Update all existing logger instances with the new config
    updateAllLoggerConfigs(config);
  }

  /** Update all existing TraceRootLogger instances with the new config */
  private static void updateAllLoggerConfigs(TraceRootConfigImpl config) {
    for (TraceRootLogger logger : loggerInstances.values()) {
      logger.config = config;
    }
  }

  /** Setup provider-specific cloud appender programmatically */
  private static void setupProviderCloudAppender(
      LoggerContext context, TraceRootConfigImpl config) {
    try {
      ch.qos.logback.core.Appender<ch.qos.logback.classic.spi.ILoggingEvent> providerAppender =
          ProviderAppenderRegistry.createAppenderForProvider(context, config);

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
    if (config != null && config.isLocalMode()) {
      // Local mode: just log the event
      logger.debug("[TraceRoot Local] Span event: {} with attributes: {}", eventName, attributes);
    }
    // Note: Complex span event implementation removed for simplicity
  }

  /** Wrapper method to add trace correlation to MDC before logging */
  private void logWithTraceCorrelation(Runnable logAction) {
    if (config != null && config.isLocalMode()) {
      // Local mode: just print info and proceed with normal logging
      System.out.println("[TraceRoot Local] Logging in local mode");
    }

    // The trace correlation will be automatically handled by TraceCorrelationConverter
    // in the Logback configuration
    logAction.run();
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

  /** Get the underlying SLF4J logger */
  public Logger getUnderlyingLogger() {
    return logger;
  }
}
