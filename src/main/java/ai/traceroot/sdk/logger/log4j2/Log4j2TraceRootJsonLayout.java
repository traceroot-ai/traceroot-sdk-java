package ai.traceroot.sdk.logger.log4j2;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;

/**
 * Custom JSON Layout for Log4j2 that merges TraceRoot required fields with user-defined custom
 * fields
 *
 * <p>Field merging strategy: 1. Traceroot-specific fields are added if they don't exist 2. For
 * conflicting keys, Traceroot values take precedence 3. User's custom non-conflicting fields are
 * appended at the end
 */
@Plugin(
    name = "Log4j2TraceRootJsonLayout",
    category = Node.CATEGORY,
    elementType = Layout.ELEMENT_TYPE,
    printObject = true)
public class Log4j2TraceRootJsonLayout extends AbstractStringLayout {

  private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

  private TraceRootConfigImpl config;
  private String userPattern;

  protected Log4j2TraceRootJsonLayout(Charset charset, String userPattern) {
    super(charset);
    this.userPattern = userPattern;
  }

  @PluginFactory
  public static Log4j2TraceRootJsonLayout createLayout(
      @PluginAttribute(value = "charset", defaultString = "UTF-8") Charset charset,
      @PluginAttribute("userPattern") String userPattern) {
    return new Log4j2TraceRootJsonLayout(charset, userPattern);
  }

  @Override
  public String toSerializable(LogEvent event) {
    Map<String, Object> mergedFields = createMergedFields(event);
    return formatAsJson(mergedFields) + "\n";
  }

  /**
   * Create merged fields: TraceRoot required fields + user's custom fields
   *
   * @param event Log4j2 log event
   * @return Merged field map with proper ordering
   */
  private Map<String, Object> createMergedFields(LogEvent event) {
    // Use LinkedHashMap to maintain insertion order
    Map<String, Object> tracerootFields = new LinkedHashMap<>();

    // 1. Add TraceRoot required fields in specific order
    addTracerootRequiredFields(tracerootFields, event);

    // 2. Parse and add user's custom fields (if pattern is provided)
    if (userPattern != null && !userPattern.isEmpty()) {
      Map<String, Object> userFields = parseUserPattern(event, userPattern);
      // Add only non-conflicting user fields at the end
      for (Map.Entry<String, Object> entry : userFields.entrySet()) {
        if (!tracerootFields.containsKey(entry.getKey())) {
          tracerootFields.put(entry.getKey(), entry.getValue());
        }
      }
    }

    return tracerootFields;
  }

  /**
   * Add TraceRoot required fields in the correct order
   *
   * @param fields Map to populate
   * @param event Log event
   */
  private void addTracerootRequiredFields(Map<String, Object> fields, LogEvent event) {
    // Service information (required fields first)
    if (config != null) {
      fields.put(
          "github_owner",
          config.getGithubOwner() != null ? config.getGithubOwner() : "traceroot-ai");
    } else {
      fields.put("github_owner", "traceroot-ai");
    }

    // Trace correlation
    Span span = Span.current();
    if (span != null) {
      SpanContext spanContext = span.getSpanContext();
      if (spanContext.isValid()
          && !spanContext.getTraceId().equals("00000000000000000000000000000000")) {
        fields.put("trace_id", spanContext.getTraceId());
      } else {
        fields.put("trace_id", "no-trace");
      }
    } else {
      fields.put("trace_id", "no-trace");
    }

    if (config != null) {
      fields.put(
          "environment", config.getEnvironment() != null ? config.getEnvironment() : "development");
    } else {
      fields.put("environment", "development");
    }

    // Log level (lowercase)
    fields.put("level", event.getLevel().toString().toLowerCase());

    // Span ID
    if (span != null) {
      SpanContext spanContext = span.getSpanContext();
      if (spanContext.isValid() && !spanContext.getSpanId().equals("0000000000000000")) {
        fields.put("span_id", spanContext.getSpanId());
      } else {
        fields.put("span_id", "no-span");
      }
    } else {
      fields.put("span_id", "no-span");
    }

    if (config != null) {
      fields.put(
          "github_repo_name",
          config.getGithubRepoName() != null ? config.getGithubRepoName() : "traceroot-sdk-java");
      fields.put(
          "service_name",
          config.getServiceName() != null ? config.getServiceName() : "standalone-java-app");
    } else {
      fields.put("github_repo_name", "traceroot-sdk-java");
      fields.put("service_name", "standalone-java-app");
    }

    // Stack trace (caller information)
    String stackTrace = Log4j2LogAppenderUtils.getCallerStackTrace(event, config);
    fields.put("stack_trace", stackTrace);

    // Message
    String message = event.getMessage().getFormattedMessage();
    if (event.getThrown() != null) {
      message = message + "\n" + Log4j2LogAppenderUtils.formatThrowable(event.getThrown());
    }
    fields.put("message", message);

    if (config != null) {
      fields.put(
          "github_commit_hash",
          config.getGithubCommitHash() != null ? config.getGithubCommitHash() : "main");
    } else {
      fields.put("github_commit_hash", "main");
    }

    // Exception/Throwable if present (optional field)
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
          exceptionStr.append("\n\tat ").append(element.toString());
        }
      }

      fields.put("exception", exceptionStr.toString());
    }

    // Timestamp in ISO 8601 format (UTC)
    Instant instant = Instant.ofEpochMilli(event.getTimeMillis());
    String timestamp = instant.toString();
    fields.put("timestamp", timestamp);
  }

  /**
   * Parse user's pattern and extract custom fields
   *
   * <p>This is a simplified parser that extracts common patterns like: - %d{format} as dateTime -
   * %thread as thread - %logger as logger - %method as method - Custom properties
   *
   * @param event Log event
   * @param pattern User's pattern string
   * @return Map of user-defined fields
   */
  private Map<String, Object> parseUserPattern(LogEvent event, String pattern) {
    Map<String, Object> userFields = new LinkedHashMap<>();

    // Parse common patterns from user's format
    // Example: {"dateTime":"%d{yyyy-MM-dd HH:mm:ss.SSS}","customField":"value"}

    if (pattern.contains("%d{") || pattern.contains("dateTime")) {
      // User wants custom datetime format
      Instant instant = Instant.ofEpochMilli(event.getTimeMillis());
      userFields.put("dateTime", instant.toString());
    }

    if (pattern.contains("%thread") || pattern.contains("thread")) {
      userFields.put("thread", event.getThreadName());
    }

    if (pattern.contains("%logger") || pattern.contains("class")) {
      userFields.put("class", event.getLoggerName());
    }

    if (pattern.contains("%method") || pattern.contains("method")) {
      StackTraceElement source = event.getSource();
      if (source != null) {
        userFields.put("method", source.getMethodName());
      } else {
        userFields.put("method", "unknown");
      }
    }

    if (pattern.contains("instanceIp") || pattern.contains("INSTANCE_IP")) {
      // Get from MDC or system property
      String instanceIp = event.getContextData().getValue("INSTANCE_IP");
      if (instanceIp != null) {
        userFields.put("instanceIp", instanceIp);
      }
    }

    if (pattern.contains("requestIp") || pattern.contains("trace-ip")) {
      String requestIp = event.getContextData().getValue("trace-ip");
      if (requestIp != null) {
        userFields.put("requestIp", requestIp);
      }
    }

    // Note: We intentionally skip conflicting fields like "level", "message"
    // because TraceRoot values should take precedence

    return userFields;
  }

  /**
   * Format map as JSON string
   *
   * @param fields Field map to format
   * @return JSON string
   */
  private String formatAsJson(Map<String, Object> fields) {
    StringBuilder json = new StringBuilder();
    json.append("{");
    boolean first = true;
    for (Map.Entry<String, Object> entry : fields.entrySet()) {
      if (!first) {
        json.append(",");
      }
      first = false;

      // Escape JSON string values
      String value = entry.getValue() == null ? "" : entry.getValue().toString();
      value = escapeJson(value);

      json.append("\"").append(entry.getKey()).append("\":\"").append(value).append("\"");
    }
    json.append("}");

    return json.toString();
  }

  /**
   * Escape special characters for JSON
   *
   * @param value String to escape
   * @return Escaped string
   */
  private String escapeJson(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  public void setConfig(TraceRootConfigImpl config) {
    this.config = config;
  }

  public void setUserPattern(String userPattern) {
    this.userPattern = userPattern;
  }
}
