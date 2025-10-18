package ai.traceroot.sdk.tracer;

import ai.traceroot.sdk.utils.SpanLogCounter;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

/**
 * SpanProcessor that cleans up log counters when spans end
 *
 * <p>This prevents memory leaks in the SpanLogCounter's internal counter map by removing entries
 * for spans that have finished.
 */
public class SpanLogCounterCleanupProcessor implements SpanProcessor {

  @Override
  public void onStart(Context parentContext, ReadWriteSpan span) {
    // Nothing to do on start
  }

  @Override
  public boolean isStartRequired() {
    return false;
  }

  @Override
  public void onEnd(ReadableSpan span) {
    // Clean up the counters for this span
    if (span != null && span.getSpanContext().isValid()) {
      SpanLogCounter.cleanupSpan(span.getSpanContext().getSpanId());
    }
  }

  @Override
  public boolean isEndRequired() {
    return true;
  }
}
