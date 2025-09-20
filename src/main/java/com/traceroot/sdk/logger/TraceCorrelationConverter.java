package com.traceroot.sdk.logger;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;

/**
 * Logback converter for adding trace correlation information to log entries
 *
 * <p>Usage in logback-spring.xml: <conversionRule conversionWord="traceId"
 * converterClass="com.traceroot.sdk.logger.TraceCorrelationConverter" /> <pattern>%d{yyyy-MM-dd
 * HH:mm:ss} [%thread] %-5level [%traceId] %logger{36} - %msg%n</pattern>
 */
public class TraceCorrelationConverter extends ClassicConverter {

  @Override
  public String convert(ILoggingEvent event) {
    Span span = Span.current();
    if (span == null) {
      return "no-trace";
    }

    SpanContext spanContext = span.getSpanContext();
    if (!spanContext.isValid()
        || spanContext.getTraceId().equals("00000000000000000000000000000000")) {
      return "no-trace";
    }

    // Convert trace ID to AWS X-Ray format (1-{8 hex chars}-{24 hex chars})
    String traceIdHex = spanContext.getTraceId();
    return String.format("1-%s-%s", traceIdHex.substring(0, 8), traceIdHex.substring(8));
  }
}
