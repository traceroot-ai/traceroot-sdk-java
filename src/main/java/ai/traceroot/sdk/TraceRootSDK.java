package ai.traceroot.sdk;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.logger.TraceRootLogger;
import ai.traceroot.sdk.tracer.TraceOptions;
import ai.traceroot.sdk.tracer.TraceRootTracer;
import ai.traceroot.sdk.tracer.utils.SpanUtils;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Main entry point for TraceRoot SDK
 *
 * <p>Usage: TraceRootSDK.initialize(config); TraceRootSDK.trace("operation-name", () -&gt; { ...
 * });
 */
public final class TraceRootSDK {

  private TraceRootSDK() {
    // Utility class
  }

  /** Initialize TraceRoot SDK with configuration */
  public static void initialize(TraceRootConfigImpl config) {
    // Initialize tracer (this will also initialize the logger)
    TraceRootTracer.initialize(config);
  }

  /** Check if SDK is initialized */
  public static boolean isInitialized() {
    return TraceRootTracer.isInitialized();
  }

  /** Get the current configuration */
  public static TraceRootConfigImpl getConfig() {
    return TraceRootTracer.getInstance().getConfig();
  }

  /** Get a TraceRoot logger */
  public static TraceRootLogger getLogger(Class<?> clazz) {
    return TraceRootLogger.getLogger(clazz);
  }

  /** Get a TraceRoot logger */
  public static TraceRootLogger getLogger(String name) {
    return TraceRootLogger.getLogger(name);
  }

  /** Get the OpenTelemetry tracer */
  public static Tracer getTracer() {
    return TraceRootTracer.getInstance().getTracer();
  }

  /** Trace a function execution */
  public static <T> T trace(String spanName, Supplier<T> function) {
    return trace(spanName, function, TraceOptions.builder().build());
  }

  /** Trace a function execution with options */
  public static <T> T trace(String spanName, Supplier<T> function, TraceOptions options) {
    if (!isInitialized()) {
      return function.get();
    }

    Tracer tracer = getTracer();
    Span span = tracer.spanBuilder(spanName).startSpan();

    try (var scope = span.makeCurrent()) {
      // Add service attributes
      TraceRootConfigImpl config = getConfig();
      if (!config.isLocalMode() && config.getInternalName() != null) {
        span.setAttribute("hash", config.getInternalName());
      }
      span.setAttribute("service_name", config.getServiceName());
      span.setAttribute("service_environment", config.getEnvironment());
      span.setAttribute("telemetry_sdk_language", "java");

      // Execute function
      T result = function.get();

      // Add return value if configured
      if (options.isTraceReturnValue() && result != null) {
        span.setAttribute("return_value", result.toString());
      }

      span.setStatus(StatusCode.OK);
      return result;
    } catch (Exception e) {
      span.recordException(e);
      span.setStatus(StatusCode.ERROR, e.getMessage());
      throw e;
    } finally {
      span.end();
    }
  }

  /** Trace a runnable execution */
  public static void trace(String spanName, Runnable runnable) {
    trace(
        spanName,
        () -> {
          runnable.run();
          return null;
        });
  }

  /** Write attributes to current span */
  public static void writeAttributesToCurrentSpan(Map<String, String> attributes) {
    SpanUtils.writeAttributesToCurrentSpan(attributes);
  }

  /** Get trace headers for HTTP requests */
  public static Map<String, String> getTraceHeaders() {
    return SpanUtils.getTraceHeaders();
  }

  /** Get current span ID */
  public static String getSpanId() {
    return SpanUtils.getSpanId();
  }

  /** Get current trace ID */
  public static String getTraceId() {
    return SpanUtils.getTraceId();
  }

  /** Check if current span is recording */
  public static boolean isRecording() {
    return SpanUtils.isRecording();
  }

  /** Get detailed span information */
  public static SpanUtils.SpanInfo getActiveSpanInfo() {
    return SpanUtils.getActiveSpanInfo();
  }

  /** Force flush all pending spans */
  public static void forceFlush() {
    if (isInitialized()) {
      TraceRootTracer.getInstance().forceFlush();
    }
  }

  /** Shutdown TraceRoot SDK */
  public static void shutdown() {
    if (isInitialized()) {
      TraceRootTracer.getInstance().shutdown();
    }
  }
}
