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

  /*
   * MEMORY SAFETY: Stack Trace Filtering Constants
   *
   * These constants define method names to exclude when finding the actual user code
   * in stack traces. Using constants instead of hard-coded strings makes the code:
   * - More maintainable and refactoring-safe
   * - Easier to update when method names change
   * - Self-documenting about what methods are being filtered
   */

  // Method names to exclude when finding actual caller (framework/logger methods)
  private static final String METHOD_GET_STACK_TRACE = "getStackTrace";
  private static final String METHOD_LOG_WITH_TRACE_CORRELATION = "logWithTraceCorrelation";

  // Class name patterns to exclude when finding actual caller
  private static final String PACKAGE_TRACEROOT_SDK = "ai.traceroot.sdk";
  private static final String PACKAGE_SLF4J = "org.slf4j";
  private static final String PACKAGE_LOGBACK = "ch.qos.logback";
  private static final String PACKAGE_JAVA_CONCURRENT = "java.util.concurrent";
  private static final String PACKAGE_JAVA_THREAD = "java.lang.Thread";

  // Logger class names to exclude
  private static final String CLASS_TRACEROOT_LOGGER = "ai.traceroot.sdk.logger.TraceRootLogger";
  private static final String CLASS_LOGGER_FACTORY = "LoggerFactory";
  private static final String CLASS_LOGGER = "Logger";

  /**
   * Clean up AspectJ synthetic method names efficiently
   *
   * <p>MEMORY SAFETY: Optimized for high-throughput logging scenarios. Uses simple string
   * operations instead of expensive regex compilation.
   *
   * @param methodName The method name that may contain AspectJ suffixes
   * @return Cleaned method name without _aroundBody suffixes
   */
  public static String cleanAspectJMethodName(String methodName) {
    // PERFORMANCE: Simple substring operation instead of regex replaceAll
    int aroundBodyIndex = methodName.indexOf("_aroundBody");
    if (aroundBodyIndex != -1) {
      return methodName.substring(0, aroundBodyIndex);
    }
    return methodName;
  }

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
        String methodName = cleanAspectJMethodName(caller.getMethodName());
        int lineNumber = caller.getLineNumber();

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
      String methodName = element.getMethodName();

      // MEMORY SAFETY: Use constants for maintainable filtering logic
      if (!className.contains("TraceRootLogger")
          && !className.contains("TraceRootTracer")
          && !className.contains(CLASS_LOGGER_FACTORY)
          && !className.contains(CLASS_LOGGER)
          && !className.startsWith(PACKAGE_SLF4J)
          && !className.startsWith(PACKAGE_LOGBACK)
          && !className.startsWith(PACKAGE_JAVA_CONCURRENT)
          && !className.startsWith(PACKAGE_JAVA_THREAD)
          && !className.startsWith(PACKAGE_TRACEROOT_SDK + ".tracer")
          && !className.startsWith(PACKAGE_TRACEROOT_SDK + ".logger")
          && !className.startsWith(PACKAGE_TRACEROOT_SDK + ".utils")
          && !methodName.equals(METHOD_GET_STACK_TRACE)) {
        return element;
      }
    }
    return callerData.length > 0 ? callerData[0] : null;
  }

  /**
   * Find the user's logging location, more permissive for direct logging calls
   *
   * @param callerData Array of stack trace elements
   * @return The user's logging location
   */
  public static StackTraceElement findUserLoggingLocation(StackTraceElement[] callerData) {
    for (StackTraceElement element : callerData) {
      String className = element.getClassName();
      String methodName = element.getMethodName();

      // MEMORY SAFETY: Use constants for maintainable filtering logic - more permissive for direct
      // logging
      if (!className.contains("TraceRootLogger")
          && !className.contains(CLASS_LOGGER_FACTORY)
          && !className.startsWith(PACKAGE_SLF4J)
          && !className.startsWith(PACKAGE_LOGBACK)
          && !className.startsWith(PACKAGE_JAVA_THREAD)
          && !className.startsWith(PACKAGE_TRACEROOT_SDK + ".logger")
          && !className.startsWith(PACKAGE_TRACEROOT_SDK + ".utils")
          && !methodName.equals(METHOD_GET_STACK_TRACE)
          && !methodName.equals(METHOD_LOG_WITH_TRACE_CORRELATION)) {
        return element;
      }
    }
    return callerData.length > 0 ? callerData[0] : null;
  }

  /*
   * MEMORY SAFETY: Performance Optimization Cache
   *
   * ORIGINAL RISK: Repeated system property calls and file system operations
   * System.getProperty("user.dir") and File.getCanonicalPath() were called
   * on every single log event, causing:
   *
   * 1. Performance degradation (thousands of system calls per second)
   * 2. Memory pressure from temporary File objects
   * 3. GC overhead from string allocations
   * 4. File system contention in high-throughput scenarios
   *
   * OUR SOLUTION: Cache + Eliminate File System Calls
   */

  // MEMORY SAFETY: Cache working directory to avoid repeated system property calls
  private static final String cachedWorkingDir;

  static {
    String dir;
    try {
      dir = System.getProperty("user.dir");
    } catch (Exception e) {
      dir = "";
    }
    cachedWorkingDir = dir;
  }

  /**
   * Construct file path from StackTraceElement
   *
   * <p>MEMORY SAFETY: Optimized for high-throughput logging with minimal allocations
   *
   * @param caller The stack trace element
   * @return File path string (absolute path, to be made relative by root path transformation)
   */
  public static String getFilePath(StackTraceElement caller) {
    String className = caller.getClassName();
    String fileName = caller.getFileName();

    if (fileName == null) {
      String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
      fileName = simpleClassName + ".java";
    }

    /*
     * MEMORY SAFETY: Cached Working Directory Access
     *
     * WHY CACHE: System.getProperty() calls are expensive and unnecessary
     * for every log event since working directory doesn't change during runtime.
     *
     * THREAD SAFETY: Static initializer ensures:
     * - Single initialization at class loading time
     * - Thread-safe initialization guaranteed by JVM
     * - No synchronization overhead during runtime
     */
    String workingDir = cachedWorkingDir;

    // Convert package to path and construct absolute path
    String packagePath = className.replace('.', '/');
    int lastSlash = packagePath.lastIndexOf('/');
    if (lastSlash >= 0) {
      packagePath = packagePath.substring(0, lastSlash + 1) + fileName;
    } else {
      packagePath = fileName;
    }

    // Construct absolute path: workingDir + /src/main/java/ + packagePath
    String absolutePath = workingDir + "/src/main/java/" + packagePath;

    /*
     * MEMORY SAFETY: No File System Calls + Optimized String Operations
     *
     * PREVIOUS RISK: new File(absolutePath).getCanonicalPath()
     * - Created temporary File objects on every log call
     * - Performed expensive file system I/O operations
     * - Could cause contention and memory pressure
     *
     * OUR SOLUTION: Efficient string normalization
     * - No object allocation when no replacement needed (common case on Unix/Linux)
     * - No file system access
     * - Consistent path format across platforms
     * - Avoids unnecessary string creation when path is already normalized
     */
    // MEMORY SAFETY: Avoid unnecessary string allocation if no backslashes are present
    return absolutePath.indexOf('\\') >= 0 ? absolutePath.replace('\\', '/') : absolutePath;
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
   * <p>MEMORY SAFETY: This method is called for every log event in high-throughput applications.
   * All operations are optimized to minimize allocations and avoid memory leaks.
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

    /*
     * MEMORY SAFETY: Stack Trace Handling Strategy
     *
     * CRITICAL TIMING ISSUE: Stack traces must be captured at log creation time,
     * not log processing time, especially for async appenders.
     *
     * THE PROBLEM WE SOLVED:
     * - Async appenders (like TencentCLS) process logs on background threads
     * - event.getCallerData() captures stack trace at processing time
     * - By processing time, call stack shows shutdown/batch methods instead of user code
     * - Result: Incorrect stack traces like "TraceRootSDK.shutdown:149"
     *
     * OUR SOLUTION:
     * 1. TraceRootLogger captures stack trace in MDC at creation time (correct thread context)
     * 2. MDC values are preserved in log events across thread boundaries
     * 3. We prefer MDC values over event.getCallerData() for accuracy
     * 4. Fallback to getCallerData only for non-TraceRootLogger logs
     */

    // Stack trace - prefer MDC value captured at log creation time
    String stackTrace = null;
    if (mdc != null) {
      // MEMORY SAFETY: Use MDC value captured at correct time with proper thread context
      stackTrace = mdc.get("traceroot.stack_trace");
    }
    if (stackTrace == null) {
      // MEMORY SAFETY: Fallback for non-TraceRootLogger logs (direct SLF4J usage)
      // Note: May be inaccurate for async processing but better than nothing
      stackTrace = getCallerStackTrace(event, config);
    }
    logData.put("stack_trace", stackTrace);

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
