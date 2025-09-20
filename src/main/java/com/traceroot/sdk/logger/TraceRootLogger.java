package com.traceroot.sdk.logger;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.traceroot.sdk.config.TraceRootConfigImpl;
import com.traceroot.sdk.types.LogLevel;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Main logger class for TraceRoot SDK with automatic trace correlation
 *
 * <p>Usage: TraceRootLogger logger = TraceRootLogger.getLogger(MyClass.class); logger.info("This
 * log will include trace correlation");
 */
public class TraceRootLogger {

  private final Logger logger;
  private final TraceRootConfigImpl config;

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
    Logger slf4jLogger = (Logger) LoggerFactory.getLogger(name);

    // Try to get config from logger context
    LoggerContext context = slf4jLogger.getLoggerContext();
    TraceRootConfigImpl config = (TraceRootConfigImpl) context.getObject("traceRootConfig");

    return new TraceRootLogger(slf4jLogger, config);
  }

  /** Initialize TraceRoot logging with configuration */
  public static void initialize(TraceRootConfigImpl config) {
    System.out.println("[TraceRoot] TraceRootLogger.initialize() called");
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    context.putObject("traceRootConfig", config);

    // Set root logger level based on config
    Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
    ch.qos.logback.classic.Level logbackLevel = convertLogLevel(config.getLogLevel());
    rootLogger.setLevel(logbackLevel);

    // Configure JSON console appender if console logging is enabled
    System.out.println("[TraceRoot] Console export enabled: " + config.isEnableLogConsoleExport());
    if (config.isEnableLogConsoleExport()) {
      System.out.println("[TraceRoot] Setting up JSON console appender");
      setupJsonConsoleAppender(context, config);
    } else {
      System.out.println("[TraceRoot] JSON console appender not setup - console export disabled");
    }

    // Configure CloudWatch appender if cloud logging is enabled
    if (config.isEnableLogCloudExport() && config.getAwsCredentials() != null) {
      System.out.println(
          "[TraceRoot] Setting up CloudWatch appender - cloud export enabled and credentials"
              + " available");
      setupCloudWatchAppender(context, config);
    } else {
      System.out.println(
          "[TraceRoot] CloudWatch appender not setup - cloud export enabled: "
              + config.isEnableLogCloudExport()
              + ", credentials available: "
              + (config.getAwsCredentials() != null));
    }
  }

  /** Setup JSON console appender programmatically */
  private static void setupJsonConsoleAppender(LoggerContext context, TraceRootConfigImpl config) {
    try {
      System.out.println("[TraceRoot] Creating JSON console appender...");
      JsonConsoleAppender jsonConsoleAppender = new JsonConsoleAppender();
      jsonConsoleAppender.setContext(context);
      jsonConsoleAppender.setConfig(config);
      jsonConsoleAppender.setName("JsonConsoleAppender");

      System.out.println("[TraceRoot] Starting JSON console appender");
      jsonConsoleAppender.start();

      // Remove existing console appender and add JSON console appender
      Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
      rootLogger.detachAppender("CONSOLE");
      rootLogger.addAppender(jsonConsoleAppender);
      System.out.println("[TraceRoot] JSON console appender added to root logger successfully");

    } catch (Exception e) {
      System.err.println("[TraceRoot] Failed to setup JSON console appender: " + e.getMessage());
    }
  }

  /** Setup CloudWatch appender programmatically */
  private static void setupCloudWatchAppender(LoggerContext context, TraceRootConfigImpl config) {
    try {
      System.out.println("[TraceRoot] Creating CloudWatch appender...");
      CloudWatchAppender cloudWatchAppender = new CloudWatchAppender();
      cloudWatchAppender.setContext(context);
      cloudWatchAppender.setConfig(config);

      // Configure appender settings
      String logGroupName =
          config.getInternalName() != null ? config.getInternalName() : "java-sdk";
      String logStreamName =
          String.format("%s-%s", config.getServiceName(), config.getEnvironment());

      System.out.println("[TraceRoot] CloudWatch log group: " + logGroupName);
      System.out.println("[TraceRoot] CloudWatch log stream: " + logStreamName);

      cloudWatchAppender.setLogGroupName(logGroupName);
      cloudWatchAppender.setLogStreamName(logStreamName);
      cloudWatchAppender.setRegion(config.getAwsRegion());
      cloudWatchAppender.setName("CloudWatchAppender");

      System.out.println("[TraceRoot] Starting CloudWatch appender for log group: " + logGroupName);
      cloudWatchAppender.start();

      // Add appender to root logger
      Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
      rootLogger.addAppender(cloudWatchAppender);
      System.out.println("[TraceRoot] CloudWatch appender added to root logger successfully");

    } catch (Exception e) {
      System.err.println("[TraceRoot] Failed to setup CloudWatch appender: " + e.getMessage());
    }
  }

  /** Shutdown TraceRoot logging and clean up resources */
  public static void shutdown() {
    try {
      LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

      // Stop all appenders
      context.stop();

      System.out.println("[TraceRoot] Logger shutdown completed");
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
    if (config != null && config.isLocalMode() && config.isTracerVerbose()) {
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
