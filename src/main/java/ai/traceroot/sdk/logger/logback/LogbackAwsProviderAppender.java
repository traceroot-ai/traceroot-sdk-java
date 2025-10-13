package ai.traceroot.sdk.logger.logback;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.logger.ProviderAppender;
import ai.traceroot.sdk.types.Provider;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;

/** AWS provider-specific log appender factory */
public class LogbackAwsProviderAppender implements ProviderAppender {

  @Override
  public boolean supportsProvider(Provider provider) {
    return provider == Provider.AWS;
  }

  @Override
  public Appender<ILoggingEvent> createAppender(LoggerContext context, TraceRootConfigImpl config) {
    if (!supportsProvider(config.getProvider()) || config.getAwsCredentials() == null) {
      return null;
    }

    try {
      LogbackCloudWatchAppender cloudWatchAppender = new LogbackCloudWatchAppender();
      cloudWatchAppender.setContext(context);
      cloudWatchAppender.setConfig(config);

      // Configure appender settings
      String logGroupName =
          config.getInternalName() != null ? config.getInternalName() : "java-sdk";
      String logStreamName =
          String.format("%s-%s", config.getServiceName(), config.getEnvironment());

      cloudWatchAppender.setLogGroupName(logGroupName);
      cloudWatchAppender.setLogStreamName(logStreamName);
      cloudWatchAppender.setRegion(config.getRegion());
      cloudWatchAppender.setName("CloudWatchAppender");

      cloudWatchAppender.start();

      return cloudWatchAppender;
    } catch (Exception e) {
      System.err.println("[TraceRoot] Failed to create AWS CloudWatch appender: " + e.getMessage());
      return null;
    }
  }
}
