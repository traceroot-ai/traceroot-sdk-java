package ai.traceroot.sdk.logger.log4j2;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.types.Provider;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;

/** Registry for creating provider-specific Log4j2 appenders */
public class Log4j2ProviderAppenderRegistry {

  /**
   * Create an appender for the configured provider
   *
   * @param context The Log4j2 logger context
   * @param config The TraceRoot configuration
   * @return The created appender, or null if provider is not supported
   */
  public static Appender createAppenderForProvider(
      LoggerContext context, TraceRootConfigImpl config) {
    if (config == null || config.getProvider() == null) {
      System.err.println("[TraceRoot] No provider configured");
      return null;
    }

    Provider provider = config.getProvider();

    switch (provider) {
      case AWS:
        return createAwsCloudWatchAppender(context, config);

      case TENCENT:
        return createTencentCLSAppender(context, config);

      default:
        System.err.println("[TraceRoot] Unsupported provider: " + provider);
        return null;
    }
  }

  /**
   * Create an AWS CloudWatch appender
   *
   * @param context The Log4j2 logger context
   * @param config The TraceRoot configuration
   * @return The created CloudWatch appender
   */
  private static Appender createAwsCloudWatchAppender(
      LoggerContext context, TraceRootConfigImpl config) {
    try {
      Log4j2CloudWatchAppender appender =
          Log4j2CloudWatchAppender.createAppender("Log4j2CloudWatchAppender", null, true);

      if (appender == null) {
        System.err.println("[TraceRoot] Failed to create CloudWatch appender");
        return null;
      }

      appender.setConfig(config);

      // Set log group and stream names (matching logback implementation)
      String logGroupName =
          config.getInternalName() != null ? config.getInternalName() : "java-sdk";
      appender.setLogGroupName(logGroupName);

      String logStreamName =
          String.format("%s-%s", config.getServiceName(), config.getEnvironment());
      appender.setLogStreamName(logStreamName);

      if (config.getAwsCredentials() != null && config.getAwsCredentials().getRegion() != null) {
        appender.setRegion(config.getAwsCredentials().getRegion());
      }

      appender.start();
      return appender;

    } catch (Exception e) {
      System.err.println("[TraceRoot] Error creating AWS CloudWatch appender: " + e.getMessage());
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Create a Tencent CLS appender
   *
   * @param context The Log4j2 logger context
   * @param config The TraceRoot configuration
   * @return The created Tencent CLS appender
   */
  private static Appender createTencentCLSAppender(
      LoggerContext context, TraceRootConfigImpl config) {
    try {
      Log4j2TencentCLSAppender appender =
          Log4j2TencentCLSAppender.createAppender("Log4j2TencentCLSAppender", null, true);

      if (appender == null) {
        System.err.println("[TraceRoot] Failed to create Tencent CLS appender");
        return null;
      }

      appender.setConfig(config);

      // Set logset name - use specified logset or default to "traceroot"
      String logset =
          (config.getTencentCredentials() != null
                  && config.getTencentCredentials().getLogset() != null)
              ? config.getTencentCredentials().getLogset()
              : "traceroot";
      appender.setLogset(logset);

      // Topic follows pattern: serviceName + "-" + environment (like internalSubName)
      String defaultTopic =
          (config.getServiceName() != null ? config.getServiceName() : "traceroot-service")
              + "-"
              + (config.getEnvironment() != null ? config.getEnvironment() : "development");
      String topic =
          config.getInternalSubName() != null ? config.getInternalSubName() : defaultTopic;
      appender.setTopic(topic);

      appender.setRegion(config.getRegion() != null ? config.getRegion() : "ap-hongkong");

      appender.start();
      return appender;

    } catch (Exception e) {
      System.err.println("[TraceRoot] Error creating Tencent CLS appender: " + e.getMessage());
      e.printStackTrace();
      return null;
    }
  }
}
