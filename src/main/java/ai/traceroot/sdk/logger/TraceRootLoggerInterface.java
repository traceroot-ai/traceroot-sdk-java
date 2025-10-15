package ai.traceroot.sdk.logger;

import ai.traceroot.sdk.types.LogLevel;
import java.util.Map;

/**
 * Common interface for TraceRoot logger implementations (Logback and Log4j2)
 *
 * <p>This interface defines the common logging methods that all logger implementations must
 * support.
 */
public interface TraceRootLoggerInterface {

  // Basic logging methods - TRACE level
  void trace(String message);

  void trace(String format, Object... args);

  void trace(String message, Throwable throwable);

  // Basic logging methods - DEBUG level
  void debug(String message);

  void debug(String format, Object... args);

  void debug(String message, Throwable throwable);

  // Basic logging methods - INFO level
  void info(String message);

  void info(String format, Object... args);

  void info(String message, Throwable throwable);

  // Basic logging methods - WARN level
  void warn(String message);

  void warn(String format, Object... args);

  void warn(String message, Throwable throwable);

  // Basic logging methods - ERROR level
  void error(String message);

  void error(String format, Object... args);

  void error(String message, Throwable throwable);

  // Log with additional context
  void logWithContext(LogLevel level, String message, Map<String, String> context);

  // Check if log levels are enabled
  boolean isTraceEnabled();

  boolean isDebugEnabled();

  boolean isInfoEnabled();

  boolean isWarnEnabled();

  boolean isErrorEnabled();

  // Get logger name
  String getName();
}
