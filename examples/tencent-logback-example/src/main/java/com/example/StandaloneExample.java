package com.example;

import ai.traceroot.sdk.TraceRootSDK;
import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.logger.TraceRootLogger;
import ai.traceroot.sdk.tracer.annotations.Trace;
import ai.traceroot.sdk.types.LogLevel;
import ai.traceroot.sdk.types.Provider;
import ai.traceroot.sdk.types.TencentCredentials;

/**
 * Standalone Java example for TraceRoot SDK with Tencent Cloud CLS using Logback
 *
 * <p>This example shows how to manually initialize TraceRoot SDK with Tencent Cloud provider for
 * logging to Tencent Cloud CLS (Cloud Log Service) using Logback as the logging backend, similar to
 * how Sentry is initialized in Java applications.
 *
 * <p>Note: TraceRootLogger will automatically detect and use Logback if it's on the classpath.
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

    // Demonstrate new SLF4J-compatible logging methods
    demonstrateNewLoggingMethods();
  }

  /**
   * Manual TraceRoot initialization with Tencent Cloud CLS - should be called early in application
   * lifecycle Similar to Sentry.init() pattern
   */
  private static void initializeTraceRoot() {
    // Create configuration for Tencent Cloud provider
    TraceRootConfigImpl config =
        TraceRootConfigImpl.builderWithEnvDefaults()
            .serviceName("tencent-logback-app")
            .githubOwner("traceroot-ai")
            .githubRepoName("traceroot-sdk-java")
            .githubCommitHash("main")
            .environment("development")
            .provider(Provider.TENCENT) // Use Tencent Cloud provider
            .enableSpanConsoleExport(false) // For local development
            .enableLogConsoleExport(true) // For local development
            .enableSpanCloudExport(true) // Enable for cloud export
            .enableLogCloudExport(true) // Enable for Tencent CLS export
            .logLevel(LogLevel.TRACE)
            .rootPath(System.getenv("TRACEROOT_ROOT_PATH")) // Get from environment
            .build();

    // Configure Tencent Cloud credentials (required for Tencent provider)
    TencentCredentials tencentCredentials = new TencentCredentials();
    tencentCredentials.setSecretId(
        System.getenv("TENCENT_SECRET_ID")); // Required: Get from environment
    tencentCredentials.setSecretKey(
        System.getenv("TENCENT_SECRET_KEY")); // Required: Get from environment
    tencentCredentials.setRegion("na-siliconvalley"); // Optional: defaults to ap-hongkong
    tencentCredentials.setLogset(
        System.getenv("TENCENT_LOGSET")); // Optional: CLS logset name (like AWS log group)
    tencentCredentials.setTraceToken(
        System.getenv("TRACE_TOKEN")); // Required: Tencent APM trace token for authentication
    tencentCredentials.setOtlpEndpoint(
        System.getenv("TENCENT_APM_ENDPOINT")); // Optional: Custom APM endpoint (overrides default pattern)

    // Set credentials on config
    config.setTencentCredentials(tencentCredentials);

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

  /** Cleanup - should be called before application shutdown Similar to Sentry shutdown pattern */
  private static void shutdown() {
    // Force flush any pending spans/logs
    TraceRootSDK.forceFlush();
    // Shutdown SDK
    TraceRootSDK.shutdown();
  }
}
