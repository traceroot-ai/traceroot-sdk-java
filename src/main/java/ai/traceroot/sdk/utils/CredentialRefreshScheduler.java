package ai.traceroot.sdk.utils;

import ai.traceroot.sdk.api.CredentialService;
import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.constants.TraceRootConstants;
import ai.traceroot.sdk.types.AwsCredentials;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Scheduler for automatic credential refresh every 12 hours */
public class CredentialRefreshScheduler {
  private static final Logger logger = LoggerFactory.getLogger(CredentialRefreshScheduler.class);

  private final ScheduledExecutorService scheduler;
  private final CredentialService credentialService;
  private final TraceRootConfigImpl config;
  private final Consumer<AwsCredentials> onCredentialsRefreshed;
  private volatile boolean isShutdown = false;

  public CredentialRefreshScheduler(
      TraceRootConfigImpl config, Consumer<AwsCredentials> onCredentialsRefreshed) {
    this.config = config;
    this.onCredentialsRefreshed = onCredentialsRefreshed;
    this.credentialService = new CredentialService();
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "traceroot-credential-refresh");
              t.setDaemon(true);
              return t;
            });
  }

  /** Start the credential refresh scheduler */
  public void start() {
    if (isShutdown) {
      logger.error("[TraceRoot] Cannot start scheduler - already shutdown");
      return;
    }

    if (!config.isEnableSpanCloudExport() && !config.isEnableLogCloudExport()) {
      if (config.isTracerVerbose()) {
        logger.debug("[TraceRoot] Cloud exports disabled - credential refresh not needed");
      }
      return;
    }

    if (config.getToken() == null || config.getToken().trim().isEmpty()) {
      if (config.isTracerVerbose()) {
        logger.debug("[TraceRoot] No token provided - credential refresh not started");
      }
      return;
    }

    long refreshIntervalHours = getRefreshInterval();

    if (config.isTracerVerbose()) {
      logger.debug(
          "[TraceRoot] Starting credential refresh scheduler with interval: {} hours",
          refreshIntervalHours);
    }

    // Schedule periodic refresh
    scheduler.scheduleAtFixedRate(
        this::refreshCredentials,
        refreshIntervalHours, // Initial delay
        refreshIntervalHours, // Period
        TimeUnit.HOURS);
  }

  /** Manually trigger credential refresh */
  public void refreshNow() {
    if (isShutdown) {
      logger.warn("[TraceRoot] Cannot refresh credentials - scheduler is shutdown");
      return;
    }

    scheduler.submit(this::refreshCredentials);
  }

  /** Stop the credential refresh scheduler */
  public void shutdown() {
    if (isShutdown) {
      return;
    }

    isShutdown = true;
    scheduler.shutdown();

    try {
      if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        scheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      scheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }

    try {
      credentialService.close();
    } catch (Exception e) {
      logger.warn("[TraceRoot] Error closing credential service: {}", e.getMessage());
    }

    if (config.isTracerVerbose()) {
      logger.debug("[TraceRoot] Credential refresh scheduler shutdown");
    }
  }

  /** Internal method to refresh credentials */
  private void refreshCredentials() {
    if (isShutdown) {
      return;
    }

    try {
      if (config.isTracerVerbose()) {
        logger.debug("[TraceRoot] Refreshing AWS credentials...");
      }

      AwsCredentials newCredentials = credentialService.fetchAwsCredentialsSync(config);

      if (newCredentials != null) {
        // Update config with new credentials
        config.setAwsCredentials(newCredentials);

        if (newCredentials.getHash() != null) {
          config.setInternalName(newCredentials.getHash());
        }

        if (newCredentials.getOtlpEndpoint() != null) {
          config.setOtlpEndpoint(newCredentials.getOtlpEndpoint());
        }

        // Notify callback
        if (onCredentialsRefreshed != null) {
          onCredentialsRefreshed.accept(newCredentials);
        }

        if (config.isTracerVerbose()) {
          logger.debug("[TraceRoot] Credentials refreshed successfully");
        }
      } else {
        logger.warn("[TraceRoot] Failed to refresh credentials");
      }
    } catch (Exception e) {
      logger.error("[TraceRoot] Error during credential refresh: {}", e.getMessage());
    }
  }

  /** Get refresh interval from configuration, system properties, or environment variables */
  private long getRefreshInterval() {
    // Check system property first
    String hoursStr =
        System.getProperty(TraceRootConstants.SYSTEM_PROPERTY_CREDENTIAL_REFRESH_HOURS);
    if (hoursStr != null && !hoursStr.trim().isEmpty()) {
      try {
        return Long.parseLong(hoursStr.trim());
      } catch (NumberFormatException e) {
        logger.warn("[TraceRoot] Invalid refresh interval in system property: {}", hoursStr);
      }
    }

    // Check environment variable
    hoursStr = System.getenv(TraceRootConstants.ENV_CREDENTIAL_REFRESH_HOURS);
    if (hoursStr != null && !hoursStr.trim().isEmpty()) {
      try {
        return Long.parseLong(hoursStr.trim());
      } catch (NumberFormatException e) {
        logger.warn("[TraceRoot] Invalid refresh interval in environment variable: {}", hoursStr);
      }
    }

    // Default to 12 hours
    return TraceRootConstants.CREDENTIAL_REFRESH_INTERVAL_HOURS;
  }
}
