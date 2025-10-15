package ai.traceroot.sdk.logger.log4j2;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;

/** Wrapper appender that intercepts file appenders and reformats output to TraceRoot JSON format */
public class Log4j2FileAppenderWrapper extends AbstractAppender {

  private TraceRootConfigImpl config;
  private Appender delegate;

  protected Log4j2FileAppenderWrapper(
      String name, Filter filter, Layout<?> layout, Appender delegate) {
    super(name, filter, layout, true, Property.EMPTY_ARRAY);
    this.delegate = delegate;
  }

  public static Log4j2FileAppenderWrapper wrap(Appender delegate) {
    return new Log4j2FileAppenderWrapper(delegate.getName(), null, null, delegate);
  }

  @Override
  public void append(LogEvent event) {
    try {
      // Create a custom logging event with our TraceRoot JSON format
      String formattedMessage = formatAsTraceRootJson(event);

      // Create a minimal log event with ONLY the formatted JSON message
      // Use a blank logger name to avoid prefix formatting
      LogEvent wrappedEvent =
          Log4jLogEvent.newBuilder()
              .setLoggerName("")
              .setLoggerFqcn("")
              .setLevel(org.apache.logging.log4j.Level.OFF) // Use OFF to avoid level formatting
              .setMessage(new SimpleMessage(formattedMessage))
              .setThreadName("")
              .setTimeMillis(event.getTimeMillis())
              .setThrown(null)
              .setContextStack(event.getContextStack())
              .build();

      // Pass to the delegate appender
      delegate.append(wrappedEvent);
    } catch (Exception e) {
      error("Failed to wrap log event: " + e.getMessage(), e);
      // Fallback to delegate
      delegate.append(event);
    }
  }

  private String formatAsTraceRootJson(LogEvent event) {
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

  private Map<String, Object> createLogData(LogEvent event) {
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
    String stackTrace = Log4j2LogAppenderUtils.getCallerStackTrace(event, config);
    logData.put("stack_trace", stackTrace);

    // Message
    logData.put("message", event.getMessage().getFormattedMessage());

    if (config != null) {
      logData.put(
          "github_commit_hash",
          config.getGithubCommitHash() != null ? config.getGithubCommitHash() : "main");
    } else {
      logData.put("github_commit_hash", "main");
    }

    // Exception/Throwable if present
    if (event.getThrown() != null) {
      Throwable throwable = event.getThrown();
      StringBuilder exceptionStr = new StringBuilder();
      exceptionStr
          .append(throwable.getClass().getName())
          .append(": ")
          .append(throwable.getMessage());

      // Add stack trace elements
      StackTraceElement[] stackTraceElements = throwable.getStackTrace();
      if (stackTraceElements != null) {
        for (StackTraceElement element : stackTraceElements) {
          exceptionStr.append("\\n\\tat ").append(element.toString());
        }
      }

      logData.put("exception", exceptionStr.toString());
    }

    // Timestamp in the same format as the example: 2025-10-15 04:31:25,010
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
    String timestamp = sdf.format(new Date(event.getTimeMillis()));
    logData.put("timestamp", timestamp);

    return logData;
  }

  public void setConfig(TraceRootConfigImpl config) {
    this.config = config;
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
  public void stop() {
    super.stop();
    if (delegate != null) {
      delegate.stop();
    }
  }
}
