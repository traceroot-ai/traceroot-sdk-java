package ai.traceroot.sdk.tracer.utils;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.util.HashMap;
import java.util.Map;

public final class SpanUtils {

  private SpanUtils() {
    // Utility class
  }

  /** Get the current active span ID for debugging purposes */
  public static String getSpanId() {
    Span span = Span.current();
    if (span == null) {
      return null;
    }

    SpanContext spanContext = span.getSpanContext();
    if (!spanContext.isValid() || spanContext.getSpanId().equals("0000000000000000")) {
      return null;
    }

    return spanContext.getSpanId();
  }

  /** Get the current trace ID */
  public static String getTraceId() {
    Span span = Span.current();
    if (span == null) {
      return null;
    }

    SpanContext spanContext = span.getSpanContext();
    if (!spanContext.isValid()
        || spanContext.getTraceId().equals("00000000000000000000000000000000")) {
      return null;
    }

    return spanContext.getTraceId();
  }

  /** Check if the current active span is recording */
  public static boolean isRecording() {
    Span span = Span.current();
    return span != null && span.isRecording();
  }

  /** Get trace headers for the current active span to propagate trace context in HTTP requests */
  public static Map<String, String> getTraceHeaders() {
    Map<String, String> headers = new HashMap<>();

    Span span = Span.current();
    if (span == null) {
      return headers;
    }

    SpanContext spanContext = span.getSpanContext();
    if (!spanContext.isValid()
        || spanContext.getTraceId().equals("00000000000000000000000000000000")) {
      return headers;
    }

    String traceId = spanContext.getTraceId();
    String spanId = spanContext.getSpanId();
    int traceFlags = spanContext.getTraceFlags().asByte();

    // W3C Trace Context standard headers
    headers.put("traceparent", String.format("00-%s-%s-%02x", traceId, spanId, traceFlags));

    // Add tracestate if available
    if (!spanContext.getTraceState().isEmpty()) {
      headers.put("tracestate", spanContext.getTraceState().toString());
    }

    // Custom headers for easier debugging and compatibility
    headers.put("x-trace-id", traceId);
    headers.put("x-span-id", spanId);

    // AWS X-Ray format trace ID for AWS services compatibility
    headers.put(
        "x-amzn-trace-id",
        String.format("Root=1-%s-%s", traceId.substring(0, 8), traceId.substring(8)));

    return headers;
  }

  /** Write custom attributes to the current active span */
  public static void writeAttributesToCurrentSpan(Map<String, String> attributes) {
    Span span = Span.current();
    if (span != null && span.isRecording()) {
      storeDictInSpan(attributes, span, false);
    }
  }

  /** Store a dictionary in a span as attributes, optionally flattening it */
  public static void storeDictInSpan(Map<String, ?> data, Span span, boolean flatten) {
    Map<String, ?> processedData = flatten ? flattenDict(data, "_") : data;

    for (Map.Entry<String, ?> entry : processedData.entrySet()) {
      Object value = entry.getValue();
      String stringValue = value != null ? value.toString() : "null";
      span.setAttribute(entry.getKey(), stringValue);
    }
  }

  /** Flatten a dictionary, joining parent/child keys with separator */
  public static Map<String, Object> flattenDict(Map<String, ?> data, String separator) {
    Map<String, Object> result = new HashMap<>();
    flattenRecursive(data, "", separator, result);
    return result;
  }

  @SuppressWarnings("unchecked")
  private static void flattenRecursive(
      Map<String, ?> obj, String prefix, String separator, Map<String, Object> result) {
    for (Map.Entry<String, ?> entry : obj.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();
      String newKey = prefix.isEmpty() ? key : prefix + separator + key;

      if (value instanceof Map) {
        flattenRecursive((Map<String, ?>) value, newKey, separator, result);
      } else {
        result.put(newKey, value);
      }
    }
  }

  /** Get detailed information about the current active span for debugging */
  public static SpanInfo getActiveSpanInfo() {
    Span span = Span.current();

    if (span == null) {
      return new SpanInfo(null, null, false, false);
    }

    SpanContext spanContext = span.getSpanContext();

    return new SpanInfo(
        spanContext.isValid() ? spanContext.getTraceId() : null,
        spanContext.isValid() ? spanContext.getSpanId() : null,
        span.isRecording(),
        true);
  }

  public static class SpanInfo {
    private final String traceId;
    private final String spanId;
    private final boolean isRecording;
    private final boolean hasActiveSpan;

    public SpanInfo(String traceId, String spanId, boolean isRecording, boolean hasActiveSpan) {
      this.traceId = traceId;
      this.spanId = spanId;
      this.isRecording = isRecording;
      this.hasActiveSpan = hasActiveSpan;
    }

    public String getTraceId() {
      return traceId;
    }

    public String getSpanId() {
      return spanId;
    }

    public boolean isRecording() {
      return isRecording;
    }

    public boolean hasActiveSpan() {
      return hasActiveSpan;
    }

    @Override
    public String toString() {
      return "SpanInfo{"
          + "traceId='"
          + traceId
          + '\''
          + ", spanId='"
          + spanId
          + '\''
          + ", isRecording="
          + isRecording
          + ", hasActiveSpan="
          + hasActiveSpan
          + '}';
    }
  }
}
