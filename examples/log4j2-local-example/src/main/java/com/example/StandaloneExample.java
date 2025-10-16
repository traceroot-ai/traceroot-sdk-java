package com.example;

import ai.traceroot.sdk.TraceRootSDK;
import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.logger.TraceRootLogger;
import ai.traceroot.sdk.tracer.annotations.Trace;
import ai.traceroot.sdk.types.LogLevel;

/**
 * Standalone Java example for TraceRoot SDK with local file logging using Log4j2
 *
 * <p>This example shows how to use TraceRoot SDK with local file logging only (no cloud export).
 * Logs are written to local files using Log4j2 as the logging backend.
 *
 * <p>Note: TraceRootLogger will automatically detect and use Log4j2 if it's on the classpath.
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

  /** Cleanup - should be called before application shutdown */
  private static void shutdown() {
    // Force flush any pending spans/logs
    TraceRootSDK.forceFlush();
    // Shutdown SDK
    TraceRootSDK.shutdown();
  }

  /** Main application logic with automatic tracing and logging */
  @Trace
  private static void runApplicationLogic() {
    // Your application logic with automatic tracing and logging
    String result = performBusinessLogic(123);
    logger.info("Result: {}", result);

    // Call additional business logic
    performAdditionalLogic();

    // Demonstrate new SLF4J-compatible logging methods
    demonstrateNewLoggingMethods();
  }

  /**
   * Manual TraceRoot initialization with local file logging only - should be called early in
   * application lifecycle Similar to Sentry.init() pattern
   */
  private static void initializeTraceRoot() {
    // Create configuration for local file logging only (no cloud export)
    TraceRootConfigImpl config =
        TraceRootConfigImpl.builderWithEnvDefaults()
            .serviceName("log4j2-local-app")
            .githubOwner("traceroot-ai")
            .githubRepoName("traceroot-sdk-java")
            .githubCommitHash("main")
            .environment("development")
            .enableSpanConsoleExport(false) // Disable console export for spans
            .enableLogConsoleExport(true) // Enable console logging
            .enableSpanCloudExport(true) // Enable tracing export to OTLP endpoint
            .enableLogCloudExport(false) // Disable cloud export for logs (local file only)
            .logLevel(LogLevel.TRACE)
            .otlpEndpoint(System.getenv("TRACEROOT_OTLP_ENDPOINT")) // Get from environment
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

  /** Demonstrate new SLF4J-compatible logging methods */
  @Trace
  private static void demonstrateNewLoggingMethods() {
    // DEBUG with exception
    try {
      throw new IllegalArgumentException("Sample exception for demonstration");
    } catch (Exception e) {
      logger.trace("Caught exception during processing", e);
    }

    // INFO with exception
    try {
      throw new RuntimeException("Another sample exception");
    } catch (Exception e) {
      logger.info("Runtime exception occurred", e);
    }

    // Parameterized logging examples (performance-optimized)
    String userId = "user123";
    String action = "update";
    logger.info("User {} performed action {}", userId, action);
    logger.debug("Processing request for user {}", userId);

    // Three parameters example
    String resource = "payment-service";
    long duration = 150;
    logger.info(
        "User {} performed action {} on resource {} in {}ms", userId, action, resource, duration);
  }
}
