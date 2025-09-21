package com.example;

import ai.traceroot.sdk.TraceRootSDK;
import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.logger.TraceRootLogger;
import ai.traceroot.sdk.tracer.annotations.Trace;
import ai.traceroot.sdk.types.LogLevel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot example for TraceRoot SDK
 *
 * <p>This example shows how to manually initialize TraceRoot SDK in a Spring Boot application,
 * similar to the standalone example.
 */
@SpringBootApplication
public class SpringBootExample {

  private static final TraceRootLogger logger = TraceRootLogger.getLogger(SpringBootExample.class);

  public static void main(String[] args) {
    SpringApplication.run(SpringBootExample.class, args);
  }

  /**
   * Manual TraceRoot initialization - should be called early in application lifecycle Similar to
   * Sentry.init() pattern
   */
  private static void initializeTraceRoot() {
    // Create configuration to match expected output format
    TraceRootConfigImpl config =
        TraceRootConfigImpl.builder()
            .serviceName("spring-boot-app")
            .githubOwner("traceroot-ai")
            .githubRepoName("traceroot-sdk-java")
            .githubCommitHash("main")
            .token(System.getenv("TRACEROOT_TOKEN"))
            .environment("development")
            .awsRegion("us-west-2")
            .enableSpanConsoleExport(false) // For local development
            .enableLogConsoleExport(true) // For local development
            .enableSpanCloudExport(true) // Enable for cloud export
            .enableLogCloudExport(true) // Enable for cloud export
            .localMode(false) // Enable for local development
            .logLevel(LogLevel.INFO)
            .rootPath(System.getenv("TRACEROOT_ROOT_PATH"))
            .build();

    // Initialize SDK (similar to Sentry.init(options))
    TraceRootSDK.initialize(config);
  }

  /** Cleanup - should be called before application shutdown Similar to Sentry shutdown pattern */
  private static void shutdown() {
    // Force flush any pending spans/logs
    TraceRootSDK.forceFlush();
    // Shutdown SDK
    TraceRootSDK.shutdown();
  }

  /** Command line runner to demonstrate functionality on startup */
  @Bean
  public CommandLineRunner demo() {
    return args -> {
      // Manual initialization (like Sentry.init())
      initializeTraceRoot();

      try {
        // Execute main application logic
        runApplicationLogic();
      } finally {
        // Cleanup before application exit
        shutdown();
      }
    };
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
}
