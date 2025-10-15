package ai.traceroot.sdk.logger.logback;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.utils.LogAppenderUtils;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/** File appender that outputs logs in TraceRoot key-value format */
public class LogbackJsonFileAppender extends RollingFileAppender<ILoggingEvent> {

  private TraceRootConfigImpl config;

  @Override
  public void start() {
    // RollingFileAppender requires an encoder to be set, but we override append() so we'll create
    // a simple one
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
  protected void subAppend(ILoggingEvent event) {
    if (!isStarted()) {
      return;
    }

    try {
      Map<String, Object> logData = createLogData(event);

      // Format as key:value pairs, one per line
      StringBuilder formattedMessage = new StringBuilder();
      for (Map.Entry<String, Object> entry : logData.entrySet()) {
        formattedMessage.append(entry.getKey()).append(":").append(entry.getValue());
        formattedMessage.append(System.lineSeparator());
      }

      // Add a blank line between log entries
      formattedMessage.append(System.lineSeparator());

      // Write using the encoder
      byte[] bytes = formattedMessage.toString().getBytes();
      getOutputStream().write(bytes);
      getOutputStream().flush();
    } catch (Exception e) {
      addError("Failed to append log event", e);
    }
  }

  private Map<String, Object> createLogData(ILoggingEvent event) {
    // Use LinkedHashMap to maintain insertion order
    Map<String, Object> logData = new LinkedHashMap<>();

    // Service information (required fields first)
    if (config != null) {
      logData.put(
          "github_owner",
          config.getGithubOwner() != null ? config.getGithubOwner() : "traceroot-ai");
    } else {
      logData.put("github_owner", "traceroot-ai");
    }

    // Trace correlation
    Span span = Span.current();
    if (span != null) {
      SpanContext spanContext = span.getSpanContext();
      if (spanContext.isValid()
          && !spanContext.getTraceId().equals("00000000000000000000000000000000")) {
        logData.put("trace_id", spanContext.getTraceId());
      } else {
        logData.put("trace_id", "no-trace");
      }
    } else {
      logData.put("trace_id", "no-trace");
    }

    if (config != null) {
      logData.put(
          "environment", config.getEnvironment() != null ? config.getEnvironment() : "development");
    } else {
      logData.put("environment", "development");
    }

    // Log level (lowercase)
    logData.put("level", event.getLevel().toString().toLowerCase());

    // Span ID
    if (span != null) {
      SpanContext spanContext = span.getSpanContext();
      if (spanContext.isValid() && !spanContext.getSpanId().equals("0000000000000000")) {
        logData.put("span_id", spanContext.getSpanId());
      } else {
        logData.put("span_id", "no-span");
      }
    } else {
      logData.put("span_id", "no-span");
    }

    if (config != null) {
      logData.put(
          "github_repo_name",
          config.getGithubRepoName() != null ? config.getGithubRepoName() : "traceroot-sdk-java");
      logData.put(
          "service_name",
          config.getServiceName() != null ? config.getServiceName() : "standalone-java-app");
    } else {
      logData.put("github_repo_name", "traceroot-sdk-java");
      logData.put("service_name", "standalone-java-app");
    }

    // Stack trace (caller information)
    String stackTrace = LogAppenderUtils.getCallerStackTrace(event, config);
    logData.put("stack_trace", stackTrace);

    // Message
    logData.put("message", event.getFormattedMessage());

    if (config != null) {
      logData.put(
          "github_commit_hash",
          config.getGithubCommitHash() != null ? config.getGithubCommitHash() : "main");
    } else {
      logData.put("github_commit_hash", "main");
    }

    // Timestamp in the same format as the example: 2025-10-15 04:31:25,010
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
    String timestamp = sdf.format(new Date(event.getTimeStamp()));
    logData.put("timestamp", timestamp);

    return logData;
  }

  public void setConfig(TraceRootConfigImpl config) {
    this.config = config;
  }
}
