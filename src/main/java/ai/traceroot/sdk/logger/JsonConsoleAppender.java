package ai.traceroot.sdk.logger;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.utils.LogAppenderUtils;
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
      // For console, use standard logback formatting with UTC timestamps
      // Format: timestamp [thread] LEVEL logger - message
      String timestamp =
          java.time.Instant.ofEpochMilli(event.getTimeStamp())
              .atZone(java.time.ZoneOffset.UTC)
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
    String stackTrace = LogAppenderUtils.getCallerStackTrace(event, config);
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

  public void setConfig(TraceRootConfigImpl config) {
    this.config = config;
  }
}
