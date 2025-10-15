package ai.traceroot.sdk.logger.log4j2;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.utils.LogAppenderUtils;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;

/** Utility class for Log4j2 appenders */
public class Log4j2LogAppenderUtils {

  /**
   * Create base log metadata from Log4j2 LogEvent
   *
   * @param event The Log4j2 log event
   * @param config TraceRoot configuration
   * @return Map containing base log metadata
   */
  public static Map<String, Object> createBaseLogMetadata(
      LogEvent event, TraceRootConfigImpl config) {
    Map<String, Object> logData = new HashMap<>();

    // Service information (required fields first)
    if (config != null) {
      logData.put(
          "service_name",
          config.getServiceName() != null ? config.getServiceName() : "standalone-java-app");
      logData.put(
          "github_commit_hash",
          config.getGithubCommitHash() != null ? config.getGithubCommitHash() : "main");
      logData.put(
          "github_owner",
          config.getGithubOwner() != null ? config.getGithubOwner() : "traceroot-ai");
      logData.put(
          "github_repo_name",
          config.getGithubRepoName() != null ? config.getGithubRepoName() : "traceroot-sdk-java");
      logData.put(
          "environment", config.getEnvironment() != null ? config.getEnvironment() : "development");
    } else {
      logData.put("service_name", "standalone-java-app");
      logData.put("github_commit_hash", "main");
      logData.put("github_owner", "traceroot-ai");
      logData.put("github_repo_name", "traceroot-sdk-java");
      logData.put("environment", "development");
    }

    // Stack trace (caller information) from ThreadContext
    String stackTrace = ThreadContext.get("traceroot.stack_trace");
    if (stackTrace == null || stackTrace.isEmpty()) {
      // Fallback to source location if available
      StackTraceElement source = event.getSource();
      if (source != null) {
        String methodName = LogAppenderUtils.cleanAspectJMethodName(source.getMethodName());
        int lineNumber = source.getLineNumber();
        String filePath = LogAppenderUtils.getFilePath(source);

        // Apply root path transformation if configured
        if (config != null
            && config.getRootPath() != null
            && filePath.startsWith(config.getRootPath())) {
          filePath = filePath.substring(config.getRootPath().length());
          if (filePath.startsWith("/")) {
            filePath = filePath.substring(1);
          }
        }

        stackTrace = String.format("%s:%s:%d", filePath, methodName, lineNumber);
      } else {
        stackTrace = "unknown:unknown:0";
      }
    }
    logData.put("stack_trace", stackTrace);

    // Log level (lowercase)
    logData.put("level", event.getLevel().name().toLowerCase());

    // Message - include exception if present
    String message = event.getMessage().getFormattedMessage();
    if (event.getThrown() != null) {
      message = message + "\n" + formatThrowable(event.getThrown());
    }
    logData.put("message", message);

    // Timestamp in ISO format
    Instant instant = Instant.ofEpochMilli(event.getTimeMillis());
    String timestamp = instant.toString();
    logData.put("timestamp", timestamp);

    return logData;
  }

  /**
   * Format throwable to string including stack trace and causes
   *
   * @param throwable The throwable from log event
   * @return Formatted stack trace string
   */
  public static String formatThrowable(Throwable throwable) {
    if (throwable == null) {
      return "";
    }

    StringBuilder sb = new StringBuilder();
    appendThrowable(sb, throwable, "");
    return sb.toString();
  }

  /** Recursively append throwable and its causes */
  private static void appendThrowable(StringBuilder sb, Throwable t, String prefix) {
    if (t == null) {
      return;
    }

    sb.append(prefix).append(t.getClass().getName()).append(": ").append(t.getMessage());
    StackTraceElement[] stackTrace = t.getStackTrace();
    if (stackTrace != null) {
      for (StackTraceElement element : stackTrace) {
        sb.append("\n");
        sb.append(prefix).append("\tat ").append(element.toString());
      }
    }

    // Handle suppressed exceptions
    Throwable[] suppressed = t.getSuppressed();
    if (suppressed != null) {
      for (Throwable suppressedT : suppressed) {
        sb.append("\n");
        sb.append(prefix).append("Suppressed: ");
        appendThrowable(sb, suppressedT, prefix + "\t");
      }
    }

    // Handle cause
    Throwable cause = t.getCause();
    if (cause != null) {
      sb.append("\n");
      sb.append(prefix).append("Caused by: ");
      appendThrowable(sb, cause, prefix);
    }
  }

  /**
   * Get caller stack trace from Log4j2 event, with fallback logic
   *
   * @param event The Log4j2 log event
   * @param config TraceRoot configuration
   * @return Formatted stack trace string
   */
  public static String getCallerStackTrace(LogEvent event, TraceRootConfigImpl config) {
    // First try ThreadContext
    String stackTrace = ThreadContext.get("traceroot.stack_trace");
    if (stackTrace != null && !stackTrace.isEmpty()) {
      return stackTrace;
    }

    // Fallback to source location
    StackTraceElement source = event.getSource();
    if (source != null) {
      String methodName = LogAppenderUtils.cleanAspectJMethodName(source.getMethodName());
      int lineNumber = source.getLineNumber();
      String filePath = LogAppenderUtils.getFilePath(source);

      // Apply root path transformation if configured
      if (config != null
          && config.getRootPath() != null
          && filePath.startsWith(config.getRootPath())) {
        filePath = filePath.substring(config.getRootPath().length());
        if (filePath.startsWith("/")) {
          filePath = filePath.substring(1);
        }
      }

      return String.format("%s:%s:%d", filePath, methodName, lineNumber);
    }

    return "unknown:unknown:0";
  }
}
