package ai.traceroot.sdk.logger;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.types.Provider;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;

/** Interface for provider-specific log appenders */
public interface ProviderAppender {

  /**
   * Check if this appender supports the given provider
   *
   * @param provider The provider to check
   * @return true if this appender supports the provider
   */
  boolean supportsProvider(Provider provider);

  /**
   * Create and configure the appender for the given provider and config
   *
   * @param context The Logback logger context
   * @param config The TraceRoot configuration
   * @return The configured appender, or null if not supported
   */
  Appender<ILoggingEvent> createAppender(LoggerContext context, TraceRootConfigImpl config);
}
