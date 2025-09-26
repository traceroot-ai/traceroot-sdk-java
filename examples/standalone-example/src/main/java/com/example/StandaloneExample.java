package com.example;

import ai.traceroot.sdk.TraceRootSDK;
import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.logger.TraceRootLogger;
import ai.traceroot.sdk.tracer.annotations.Trace;
import ai.traceroot.sdk.types.LogLevel;

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
      // Execute main application logic
      runApplicationLogic();
    } finally {
      // Cleanup before application exit
      shutdown();
    }
  }

  /** Main application logic with automatic tracing and logging */
  @Trace
  private static void runApplicationLogic() {
    // Your application logic with automatic tracing and logging
    String result = performBusinessLogic(123);
    logger.info("Result: {}", result);

    // Call additional business logic
    performAdditionalLogic();
  }

  /**
   * Manual TraceRoot initialization - should be called early in application lifecycle Similar to
   * Sentry.init() pattern
   */
  private static void initializeTraceRoot() {
    // Create configuration to match expected output format
    TraceRootConfigImpl config =
        TraceRootConfigImpl.builderWithEnvDefaults()
            .serviceName("standalone-java-app")
            .githubOwner("traceroot-ai")
            .githubRepoName("traceroot-sdk-java")
            .githubCommitHash("main")
            .token(System.getenv("TRACEROOT_TOKEN")) // Get from environment
            .environment("development")
            .region("us-west-2")
            .enableSpanConsoleExport(false) // For local development
            .enableLogConsoleExport(true) // For local development
            .enableSpanCloudExport(System.getenv("TRACEROOT_ENABLE_SPAN_CLOUD_EXPORT")) // Enable for cloud export
            .enableLogCloudExport(System.getenv("TRACEROOT_ENABLE_LOG_CLOUD_EXPORT")) // Enable for cloud export
            .localMode(false) // Enable for local development
            .logLevel(LogLevel.INFO)
            .rootPath(System.getenv("TRACEROOT_ROOT_PATH")) // Get from environment
            .build();

    // Initialize SDK (similar to Sentry.init(options))
    TraceRootSDK.initialize(config);
  }

  /** Simple business logic with tracing and logging */
  @Trace(spanName = "process-data-name", traceParams = true)
  private static String performBusinessLogic(int businessNumber) {
    logger.info("Processing data for business number: " + businessNumber);
    return "processed-data";
  }

  /** Alternative example showing @Trace without explicit span name (uses method name) */
  @Trace
  private static void performAdditionalLogic() {
    logger.info("Performing additional business logic...");
  }

  /** Cleanup - should be called before application shutdown Similar to Sentry shutdown pattern */
  private static void shutdown() {
    // Force flush any pending spans/logs
    TraceRootSDK.forceFlush();
    // Shutdown SDK
    TraceRootSDK.shutdown();
  }
}
