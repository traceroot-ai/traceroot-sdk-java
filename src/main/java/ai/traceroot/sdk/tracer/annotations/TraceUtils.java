package ai.traceroot.sdk.tracer.annotations;

import ai.traceroot.sdk.tracer.TraceOptions;

/** Utility class for working with @Trace annotations and TraceOptions. */
public final class TraceUtils {

  private TraceUtils() {
    // Utility class - prevent instantiation
  }

  /**
   * Converts a @Trace annotation to a TraceOptions object.
   *
   * @param trace The @Trace annotation
   * @param methodName The method name to use if no span name is specified
   * @return TraceOptions configured with the annotation's values
   */
  public static TraceOptions toTraceOptions(Trace trace, String methodName) {
    TraceOptions.Builder builder = TraceOptions.builder();

    // Handle span name precedence: value() takes precedence over spanName()
    String effectiveSpanName = trace.value().isEmpty() ? trace.spanName() : trace.value();
    if (!effectiveSpanName.isEmpty()) {
      builder.spanName(effectiveSpanName);
    }

    if (!trace.spanNameSuffix().isEmpty()) {
      builder.spanNameSuffix(trace.spanNameSuffix());
    }

    if (trace.traceParams()) {
      if (trace.tracedParamNames().length > 0) {
        builder.traceParams(trace.tracedParamNames());
      } else {
        builder.traceAllParams();
      }
    }

    if (trace.traceReturnValue()) {
      builder.traceReturnValue();
    }

    builder.flattenAttributes(trace.flattenAttributes());

    return builder.build();
  }
}
