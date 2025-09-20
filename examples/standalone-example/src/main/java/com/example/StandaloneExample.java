package com.example;

import com.traceroot.sdk.TraceRootSDK;
import com.traceroot.sdk.config.TraceRootConfigImpl;
import com.traceroot.sdk.logger.TraceRootLogger;
import com.traceroot.sdk.types.LogLevel;

/**
 * Standalone Java example for TraceRoot SDK
 *
 * <p>This example shows how to manually initialize TraceRoot SDK similar to how Sentry is
 * initialized in Java applications.
 */
public class StandaloneExample {

  private static final TraceRootLogger logger = TraceRootLogger.getLogger(StandaloneExample.class);

  public static void main(String[] args) {
    // Manual initialization (like Sentry.init())
    initializeTraceRoot();

    try {
      // Your application logic with automatic tracing and logging
      performBusinessLogic();
    } finally {
      // Cleanup before application exit
      shutdown();
    }
  }

  /**
   * Manual TraceRoot initialization - should be called early in application lifecycle Similar to
   * Sentry.init() pattern
   */
  private static void initializeTraceRoot() {
    // Create configuration to match expected output format
    TraceRootConfigImpl config =
        TraceRootConfigImpl.builder()
            .serviceName("standalone-java-app")
            .githubOwner("traceroot-ai")
            .githubRepoName("traceroot-sdk-java")
            .githubCommitHash("main")
            .token(System.getenv("TRACEROOT_TOKEN")) // Get from environment
            .environment("development")
            .awsRegion("us-west-2")
            .enableSpanConsoleExport(false) // For local development
            .enableLogConsoleExport(true) // For local development
            .enableSpanCloudExport(true) // Enable for cloud export
            .enableLogCloudExport(true) // Enable for cloud export
            .localMode(false) // Enable for local development
            .logLevel(LogLevel.INFO)
            .build();

    // Initialize SDK (similar to Sentry.init(options))
    TraceRootSDK.initialize(config);
  }

  /** Simple business logic with tracing and logging */
  private static void performBusinessLogic() {
    String result =
        TraceRootSDK.trace(
            "process-data",
            () -> {
              logger.info("Processing data...");
              return "processed-data";
            });
    // This will not be shown in the TraceRoot UI because it is not within a trace event
    logger.info("Result: {}", result);
  }

  /** Cleanup - should be called before application shutdown Similar to Sentry shutdown pattern */
  private static void shutdown() {
    // Force flush any pending spans/logs
    TraceRootSDK.forceFlush();
    // Shutdown SDK
    TraceRootSDK.shutdown();
  }
}
