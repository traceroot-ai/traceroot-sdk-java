package ai.traceroot.sdk.logger;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Console appender that outputs logs in TraceRoot JSON format */
public class JsonConsoleAppender extends ConsoleAppender<ILoggingEvent> {

  private TraceRootConfigImpl config;
  private final ObjectMapper objectMapper =
      new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

  @Override
  public void start() {
    // ConsoleAppender requires an encoder to be set, but we override append() so we'll create a
    // simple one
    if (getEncoder() == null) {
      ch.qos.logback.classic.encoder.PatternLayoutEncoder encoder =
          new ch.qos.logback.classic.encoder.PatternLayoutEncoder();
      encoder.setContext(getContext());
      encoder.setPattern("%msg%n"); // Simple pattern since we override append()
      encoder.start();
      setEncoder(encoder);
    }

    super.start();
  }

  @Override
  protected void append(ILoggingEvent event) {
    try {
      // For console, use standard logback formatting
      // Format: timestamp [thread] LEVEL logger - message
      String timestamp =
          java.time.Instant.ofEpochMilli(event.getTimeStamp())
              .atZone(java.time.ZoneId.systemDefault())
              .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));

      String formattedMessage =
          String.format(
              "%s [%s] %-5s %s - %s",
              timestamp,
              event.getThreadName(),
              event.getLevel(),
              event.getLoggerName(),
              event.getFormattedMessage());

      // Write directly to the output stream
      byte[] bytes = (formattedMessage + System.lineSeparator()).getBytes();
      getOutputStream().write(bytes);
      getOutputStream().flush();
    } catch (Exception e) {
      addError("Failed to append log event", e);
      e.printStackTrace();
    }
  }

  private Map<String, Object> createLogData(ILoggingEvent event) {
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

    // Stack trace (caller information)
    String stackTrace = getCallerStackTrace(event);
    logData.put("stack_trace", stackTrace);

    // Log level (lowercase)
    logData.put("level", event.getLevel().toString().toLowerCase());

    // Message
    logData.put("message", event.getFormattedMessage());

    // Timestamp in ISO format
    Instant instant = Instant.ofEpochMilli(event.getTimeStamp());
    String timestamp = instant.toString();
    logData.put("timestamp", timestamp);

    // Trace correlation
    Span span = Span.current();
    if (span != null) {
      SpanContext spanContext = span.getSpanContext();
      if (spanContext.isValid()
          && !spanContext.getTraceId().equals("00000000000000000000000000000000")) {
        logData.put("trace_id", spanContext.getTraceId());
        logData.put("span_id", spanContext.getSpanId());
      } else {
        logData.put("trace_id", "no-trace");
        logData.put("span_id", "no-span");
      }
    } else {
      logData.put("trace_id", "no-trace");
      logData.put("span_id", "no-span");
    }

    return logData;
  }

  private String getCallerStackTrace(ILoggingEvent event) {
    StackTraceElement[] callerData = event.getCallerData();
    if (callerData != null && callerData.length > 0) {
      // Find the actual calling code, skipping logger framework frames
      StackTraceElement caller = findActualCaller(callerData);
      if (caller != null) {
        String methodName = caller.getMethodName();
        int lineNumber = caller.getLineNumber();
        String fileName = caller.getFileName();

        // Clean up AspectJ synthetic method names (remove _aroundBodyN suffixes)
        if (methodName.contains("_aroundBody")) {
          methodName = methodName.replaceAll("_aroundBody\\d*", "");
        }

        // Get the file path - try to construct absolute path or use class-based path
        String filePath = getFilePath(caller);

        // Apply root path transformation if configured
        if (config != null
            && config.getRootPath() != null
            && filePath.startsWith(config.getRootPath())) {
          filePath = filePath.substring(config.getRootPath().length());
          // Remove leading slash if present
          if (filePath.startsWith("/")) {
            filePath = filePath.substring(1);
          }
        }

        return String.format("%s:%s:%d", filePath, methodName, lineNumber);
      }
    }
    return "Unknown:unknown:0";
  }

  private StackTraceElement findActualCaller(StackTraceElement[] callerData) {
    // Skip logger framework classes to find the actual calling code
    for (StackTraceElement element : callerData) {
      String className = element.getClassName();

      // Skip TraceRootLogger, LoggerFactory, and other logging framework classes
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
    // Fallback to the first element if we can't find a better one
    return callerData.length > 0 ? callerData[0] : null;
  }

  private String getFilePath(StackTraceElement caller) {
    String className = caller.getClassName();
    String fileName = caller.getFileName();

    if (fileName == null) {
      // Fallback to constructed filename from class name
      String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
      fileName = simpleClassName + ".java";
    }

    // Try to find the actual source file path
    String actualFilePath = findActualSourceFile(className, fileName);

    // If we have a root path and found the actual file, remove the root path
    if (config != null && config.getRootPath() != null && actualFilePath != null) {
      if (actualFilePath.startsWith(config.getRootPath())) {
        String relativePath = actualFilePath.substring(config.getRootPath().length());
        if (relativePath.startsWith("/")) {
          relativePath = relativePath.substring(1);
        }
        return relativePath;
      }
    }

    // Fallback to package-based path if actual file not found
    String packagePath = className.replace('.', '/');
    int lastSlash = packagePath.lastIndexOf('/');
    if (lastSlash >= 0) {
      packagePath = packagePath.substring(0, lastSlash + 1) + fileName;
    } else {
      packagePath = fileName;
    }

    return packagePath;
  }

  private String findActualSourceFile(String className, String fileName) {
    try {
      // Try to get the actual source file location from the class
      Class<?> clazz = Class.forName(className);
      java.security.CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
      if (codeSource != null) {
        java.net.URL location = codeSource.getLocation();
        if (location != null) {
          String classPath = location.getPath();

          // If it's a .class file in target/classes, try to find corresponding source
          if (classPath.contains("/target/classes")) {
            String sourcePath = classPath.replace("/target/classes", "/src/main/java");
            String packagePath = className.replace('.', '/');
            int lastSlash = packagePath.lastIndexOf('/');
            if (lastSlash >= 0) {
              packagePath = packagePath.substring(0, lastSlash + 1) + fileName;
            } else {
              packagePath = fileName;
            }
            String fullSourcePath = sourcePath + "/" + packagePath;

            java.io.File sourceFile = new java.io.File(fullSourcePath);
            if (sourceFile.exists()) {
              return sourceFile.getAbsolutePath();
            }
          }
        }
      }
    } catch (Exception e) {
      // Ignore errors, fallback to package path
    }
    return null;
  }

  public void setConfig(TraceRootConfigImpl config) {
    this.config = config;
  }
}
