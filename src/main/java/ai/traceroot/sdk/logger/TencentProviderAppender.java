package ai.traceroot.sdk.logger;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.types.Provider;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;

/**
 * Tencent Cloud provider-specific log appender factory TODO: Implement Tencent Cloud CLS
 * integration
 */
public class TencentProviderAppender implements ProviderAppender {

  @Override
  public boolean supportsProvider(Provider provider) {
    return provider == Provider.TENCENT;
  }

  @Override
  public Appender<ILoggingEvent> createAppender(LoggerContext context, TraceRootConfigImpl config) {
    if (!supportsProvider(config.getProvider())) {
      return null;
    }

    // TODO: Implement Tencent Cloud CLS appender
    System.out.println(
        "[TraceRoot] Tencent Cloud CLS appender not yet implemented. Using console logging.");
    return null;
  }
}
