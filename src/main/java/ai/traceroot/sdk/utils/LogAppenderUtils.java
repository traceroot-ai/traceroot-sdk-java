package ai.traceroot.sdk.utils;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Utility class for shared functionality between log appenders */
public class LogAppenderUtils {

  /**
   * Extract stack trace information from a log event
   *
   * @param event The log event
   * @param config TraceRoot configuration for root path transformation
   * @return Stack trace string in format "filepath:methodname:linenumber"
   */
  public static String getCallerStackTrace(ILoggingEvent event, TraceRootConfigImpl config) {
    StackTraceElement[] callerData = event.getCallerData();
    if (callerData != null && callerData.length > 0) {
      StackTraceElement caller = findActualCaller(callerData);
      if (caller != null) {
        String methodName = caller.getMethodName();
        int lineNumber = caller.getLineNumber();

        // Clean up AspectJ synthetic method names
        if (methodName.contains("_aroundBody")) {
          methodName = methodName.replaceAll("_aroundBody\\d*", "");
        }

        String filePath = getFilePath(caller);

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
    }
    return "Unknown:unknown:0";
  }

  /**
   * Find the actual calling code, skipping logger framework classes
   *
   * @param callerData Array of stack trace elements
   * @return The actual caller or first element as fallback
   */
  public static StackTraceElement findActualCaller(StackTraceElement[] callerData) {
    for (StackTraceElement element : callerData) {
      String className = element.getClassName();
      if (!className.contains("TraceRootLogger")
          && !className.contains("LoggerFactory")
          && !className.contains("Logger")
          && !className.startsWith("org.slf4j")
          && !className.startsWith("ch.qos.logback")
          && !className.startsWith("java.util.concurrent")
          && !className.startsWith("java.lang.Thread")) {
        return element;
      }
    }
    return callerData.length > 0 ? callerData[0] : null;
  }

  /**
   * Construct file path from StackTraceElement
   *
   * @param caller The stack trace element
   * @return File path string
   */
  public static String getFilePath(StackTraceElement caller) {
    String className = caller.getClassName();
    String fileName = caller.getFileName();

    if (fileName == null) {
      String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
      fileName = simpleClassName + ".java";
    }

    // Convert package to path
    String packagePath = className.replace('.', '/');
    int lastSlash = packagePath.lastIndexOf('/');
    if (lastSlash >= 0) {
      packagePath = packagePath.substring(0, lastSlash + 1) + fileName;
    } else {
      packagePath = fileName;
    }

    return packagePath;
  }

  /**
   * Create a scheduled executor service for batch processing
   *
   * @param threadName Name prefix for the daemon thread
   * @param task The task to run periodically
   * @param intervalSeconds Interval between executions in seconds
   * @return ScheduledExecutorService
   */
  public static ScheduledExecutorService createBatchProcessor(
      String threadName, Runnable task, int intervalSeconds) {
    ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, threadName);
              t.setDaemon(true);
              return t;
            });

    scheduler.scheduleAtFixedRate(task, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    return scheduler;
  }

  /**
   * Create base log metadata that's common across all appenders
   *
   * @param event The log event
   * @param config TraceRoot configuration
   * @return Map with common log metadata
   */
  public static Map<String, Object> createBaseLogMetadata(
      ILoggingEvent event, TraceRootConfigImpl config) {
    Map<String, Object> logData = new LinkedHashMap<>();

    // Service metadata
    logData.put(
        "service_name",
        config != null && config.getServiceName() != null
            ? config.getServiceName()
            : "traceroot-service");

    logData.put(
        "github_commit_hash",
        config != null && config.getGithubCommitHash() != null
            ? config.getGithubCommitHash()
            : "main");

    logData.put(
        "github_owner",
        config != null && config.getGithubOwner() != null
            ? config.getGithubOwner()
            : "traceroot-ai");

    logData.put(
        "github_repo_name",
        config != null && config.getGithubRepoName() != null
            ? config.getGithubRepoName()
            : "traceroot-sdk-java");

    logData.put(
        "environment",
        config != null && config.getEnvironment() != null
            ? config.getEnvironment()
            : "development");

    // Add MDC context if available
    Map<String, String> mdc = event.getMDCPropertyMap();
    if (mdc != null && !mdc.isEmpty()) {
      String requestId = mdc.get("requestId");
      if (requestId != null) {
        logData.put("requestId", requestId);
      }
    }

    // Stack trace
    logData.put("stack_trace", getCallerStackTrace(event, config));

    // Log level
    logData.put("level", event.getLevel().toString().toLowerCase());

    // Message
    logData.put("message", event.getFormattedMessage());

    // Timestamp
    java.time.Instant instant = java.time.Instant.ofEpochMilli(event.getTimeStamp());
    java.time.ZonedDateTime zdt = instant.atZone(java.time.ZoneId.systemDefault());
    String formattedTimestamp =
        zdt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"));
    logData.put("timestamp", formattedTimestamp);

    return logData;
  }

  /**
   * Add trace correlation to log metadata
   *
   * @param logData The log data map to add trace info to
   * @param useAwsXRayFormat Whether to format trace ID for AWS X-Ray
   */
  public static void addTraceCorrelation(Map<String, Object> logData, boolean useAwsXRayFormat) {
    Span span = Span.current();
    if (span != null) {
      SpanContext spanContext = span.getSpanContext();
      if (spanContext.isValid()
          && !spanContext.getTraceId().equals("00000000000000000000000000000000")) {
        String traceId = spanContext.getTraceId();

        if (useAwsXRayFormat) {
          // Format trace_id in AWS X-Ray format: "1-{first 8 chars}-{remaining 24 chars}"
          traceId = String.format("1-%s-%s", traceId.substring(0, 8), traceId.substring(8));
        }

        logData.put("trace_id", traceId);
        logData.put("span_id", spanContext.getSpanId());
      } else {
        logData.put("trace_id", "no-trace");
        logData.put("span_id", "no-span");
      }
    } else {
      logData.put("trace_id", "no-trace");
      logData.put("span_id", "no-span");
    }
  }

  /**
   * Safely shutdown a scheduled executor service
   *
   * @param scheduler The scheduler to shutdown
   * @param timeoutSeconds Timeout for graceful shutdown
   */
  public static void shutdownScheduler(ScheduledExecutorService scheduler, int timeoutSeconds) {
    if (scheduler != null) {
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
          scheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        scheduler.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }
}
