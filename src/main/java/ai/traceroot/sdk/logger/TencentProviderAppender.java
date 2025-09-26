package ai.traceroot.sdk.logger;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.types.Provider;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;

/** Tencent Cloud provider-specific log appender factory for CLS integration */
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

    if (config.getTencentCredentials() == null) {
      System.err.println("[TraceRoot] DEBUG: Tencent credentials are null, returning null");
      return null;
    }

    try {
      TencentCLSAppender clsAppender = new TencentCLSAppender();
      clsAppender.setContext(context);
      clsAppender.setConfig(config);
      clsAppender.setName("TencentCLSAppender");

      // Set default logset (like AWS log group) and topic (like AWS log stream) from config
      String logset = config.getInternalName() != null ? config.getInternalName() : "traceroot";
      clsAppender.setLogset(logset);

      // Topic follows pattern: serviceName + "-" + environment (like internalSubName)
      String defaultTopic =
          (config.getServiceName() != null ? config.getServiceName() : "traceroot-service")
              + "-"
              + (config.getEnvironment() != null ? config.getEnvironment() : "development");
      String topic =
          config.getInternalSubName() != null ? config.getInternalSubName() : defaultTopic;
      clsAppender.setTopic(topic);

      clsAppender.setRegion(config.getRegion() != null ? config.getRegion() : "ap-hongkong");

      clsAppender.start();
      return clsAppender;

    } catch (Exception e) {
      System.err.println("[TraceRoot] Failed to create Tencent CLS appender: " + e.getMessage());
      return null;
    }
  }
}
