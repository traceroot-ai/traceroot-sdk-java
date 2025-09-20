package com.traceroot.sdk.logger;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.traceroot.sdk.config.TraceRootConfigImpl;
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
  protected void append(ILoggingEvent event) {
    try {
      // Create log data in the expected JSON format
      Map<String, Object> logData = createLogData(event);
      String jsonMessage = objectMapper.writeValueAsString(logData);

      // Write directly to the output stream
      byte[] bytes = (jsonMessage + System.lineSeparator()).getBytes();
      getOutputStream().write(bytes);
      getOutputStream().flush();
    } catch (Exception e) {
      addError("Failed to append JSON log event", e);
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
      StackTraceElement caller = callerData[0];
      String methodName = caller.getMethodName();
      int lineNumber = caller.getLineNumber();

      // Format similar to TypeScript example: "examples/simple-example.ts:makeRequest:9"
      String className = caller.getClassName();
      String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
      return String.format("%s:%s:%d", simpleClassName + ".java", methodName, lineNumber);
    }
    return "Unknown:unknown:0";
  }

  public void setConfig(TraceRootConfigImpl config) {
    this.config = config;
  }
}
