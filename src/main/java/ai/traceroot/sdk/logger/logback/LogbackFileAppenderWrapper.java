package ai.traceroot.sdk.logger.logback;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.utils.LogAppenderUtils;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wrapper appender that intercepts file appenders and reformats output to TraceRoot JSON key-value
 * format
 */
public class LogbackFileAppenderWrapper extends UnsynchronizedAppenderBase<ILoggingEvent> {

  private TraceRootConfigImpl config;
  private Appender<ILoggingEvent> delegate;

  public LogbackFileAppenderWrapper(Appender<ILoggingEvent> delegate) {
    this.delegate = delegate;
  }

  @Override
  public void start() {
    // Make sure delegate is started
    if (!delegate.isStarted()) {
      delegate.start();
    }
    super.start();
  }

  @Override
  protected void append(ILoggingEvent event) {
    try {
      // Create a custom logging event with our TraceRoot format
      String formattedMessage = formatAsTraceRootJson(event);

      // Create a new logging event with the formatted message
      ch.qos.logback.classic.spi.LoggingEvent wrappedEvent =
          new ch.qos.logback.classic.spi.LoggingEvent(
              event.getLoggerName(),
              (ch.qos.logback.classic.Logger)
                  org.slf4j.LoggerFactory.getLogger(event.getLoggerName()),
              event.getLevel(),
              formattedMessage,
              null,
              null);
      wrappedEvent.setTimeStamp(event.getTimeStamp());
      wrappedEvent.setThreadName(event.getThreadName());

      // Pass to the delegate appender
      delegate.doAppend(wrappedEvent);
    } catch (Exception e) {
      System.err.println("[TraceRoot] Failed to wrap log event: " + e.getMessage());
      e.printStackTrace();
      // Fallback to delegate
      delegate.doAppend(event);
    }
  }

  private String formatAsTraceRootJson(ILoggingEvent event) {
    Map<String, Object> logData = createLogData(event);

    // Format as JSON for structured logging (parseable by log agents)
    StringBuilder json = new StringBuilder();
    json.append("{");
    boolean first = true;
    for (Map.Entry<String, Object> entry : logData.entrySet()) {
      if (!first) {
        json.append(",");
      }
      first = false;

      // Escape JSON string values
      String value = entry.getValue() == null ? "" : entry.getValue().toString();
      value =
          value
              .replace("\\", "\\\\")
              .replace("\"", "\\\"")
              .replace("\n", "\\n")
              .replace("\r", "\\r")
              .replace("\t", "\\t");

      json.append("\"").append(entry.getKey()).append("\":\"").append(value).append("\"");
    }
    json.append("}");

    return json.toString();
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

    // Exception/Throwable if present
    if (event.getThrowableProxy() != null) {
      ch.qos.logback.classic.spi.IThrowableProxy throwableProxy = event.getThrowableProxy();
      StringBuilder exceptionStr = new StringBuilder();
      exceptionStr
          .append(throwableProxy.getClassName())
          .append(": ")
          .append(throwableProxy.getMessage());

      // Add stack trace elements
      ch.qos.logback.classic.spi.StackTraceElementProxy[] stackTraceElements =
          throwableProxy.getStackTraceElementProxyArray();
      if (stackTraceElements != null) {
        for (ch.qos.logback.classic.spi.StackTraceElementProxy element : stackTraceElements) {
          exceptionStr.append("\\n\\tat ").append(element.toString());
        }
      }

      logData.put("exception", exceptionStr.toString());
    }

    // Timestamp in ISO 8601 format (UTC)
    Instant instant = Instant.ofEpochMilli(event.getTimeStamp());
    String timestamp = instant.toString();
    logData.put("timestamp", timestamp);

    return logData;
  }

  public void setConfig(TraceRootConfigImpl config) {
    this.config = config;
  }

  @Override
  public void stop() {
    super.stop();
    if (delegate != null) {
      delegate.stop();
    }
  }
}
