package ai.traceroot.sdk.logger;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.types.Provider;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import java.util.ArrayList;
import java.util.List;

/** Registry for provider-specific appenders */
public class ProviderAppenderRegistry {

  private static final List<ProviderAppender> appenders = new ArrayList<>();

  static {
    // Register all provider appenders
    appenders.add(new AwsProviderAppender());
    appenders.add(new TencentProviderAppender());
  }

  /**
   * Create an appender for the specified provider
   *
   * @param context The Logback logger context
   * @param config The TraceRoot configuration
   * @return The configured appender, or null if no suitable appender found
   */
  public static Appender<ILoggingEvent> createAppenderForProvider(
      LoggerContext context, TraceRootConfigImpl config) {
    Provider provider = config.getProvider();

    for (ProviderAppender appender : appenders) {
      if (appender.supportsProvider(provider)) {
        return appender.createAppender(context, config);
      }
    }

    System.err.println("[TraceRoot] No appender found for provider: " + provider);
    return null;
  }

  /**
   * Check if a provider is supported
   *
   * @param provider The provider to check
   * @return true if the provider is supported
   */
  public static boolean isProviderSupported(Provider provider) {
    return appenders.stream().anyMatch(appender -> appender.supportsProvider(provider));
  }
}
