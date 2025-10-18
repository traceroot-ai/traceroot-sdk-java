package ai.traceroot.sdk.utils;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for incrementing log count attributes on the current span
 *
 * <p>This tracks the number of logs at each level (trace, debug, info, warn, error) for the current
 * span, similar to the Python SDK implementation.
 *
 * <p>Since OpenTelemetry Java doesn't allow reading attribute values from spans, we maintain an
 * internal counter map keyed by span ID. When a span ends, the counter is removed.
 */
public class SpanLogCounter {

  // Map of span ID to log counters for that span
  private static final Map<String, Map<String, Long>> spanCounters = new ConcurrentHashMap<>();

  private SpanLogCounter() {
    // Utility class
  }

  /** Increment the trace log count for the current span */
  public static void incrementTraceLogCount() {
    incrementLogCount("num_trace_logs");
  }

  /** Increment the debug log count for the current span */
  public static void incrementDebugLogCount() {
    incrementLogCount("num_debug_logs");
  }

  /** Increment the info log count for the current span */
  public static void incrementInfoLogCount() {
    incrementLogCount("num_info_logs");
  }

  /** Increment the warning log count for the current span */
  public static void incrementWarnLogCount() {
    incrementLogCount("num_warning_logs");
  }

  /** Increment the error log count for the current span */
  public static void incrementErrorLogCount() {
    incrementLogCount("num_error_logs");
  }

  /**
   * Increment the log count attribute for the current span
   *
   * @param attributeName the name of the attribute to increment
   */
  private static void incrementLogCount(String attributeName) {
    try {
      Span span = Span.current();
      if (span != null && span.isRecording()) {
        SpanContext spanContext = span.getSpanContext();

        // Only increment if we have a valid span (not invalid/noop span)
        if (spanContext.isValid()) {
          String spanId = spanContext.getSpanId();

          // Get or create counter map for this span
          Map<String, Long> counters =
              spanCounters.computeIfAbsent(spanId, k -> new ConcurrentHashMap<>());

          // Increment the counter
          Long newCount = counters.merge(attributeName, 1L, Long::sum);

          // Set the attribute on the span
          span.setAttribute(attributeName, newCount);
        }
      }
    } catch (Exception e) {
      // Don't let span attribute errors interfere with logging
      // Silently ignore
    }
  }

  /**
   * Clean up counters for a span (call this when a span ends)
   *
   * @param spanId the span ID to clean up
   */
  public static void cleanupSpan(String spanId) {
    if (spanId != null) {
      spanCounters.remove(spanId);
    }
  }

  /**
   * Get current count for testing purposes
   *
   * @param spanId the span ID
   * @param attributeName the attribute name
   * @return the current count, or 0 if not found
   */
  static long getCount(String spanId, String attributeName) {
    Map<String, Long> counters = spanCounters.get(spanId);
    if (counters != null) {
      return counters.getOrDefault(attributeName, 0L);
    }
    return 0L;
  }
}
