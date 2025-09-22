package com.example;

import ai.traceroot.sdk.TraceRootSDK;
import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.types.LogLevel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class App {

  public static void main(String[] args) {
    SpringApplication.run(App.class, args);
  }

  /**
   * Initialize TraceRoot SDK after Spring Boot has fully started.
   *
   * <p>We use ApplicationReadyEvent instead of @PostConstruct or main() because: - Spring Boot
   * reconfigures logback during startup, which would stop any CloudWatch appenders added too early
   * - ApplicationReadyEvent fires after all Spring Boot initialization is complete, ensuring
   * TraceRoot's logback configuration persists
   */
  @EventListener(ApplicationReadyEvent.class)
  public void initializeTraceRoot() {
    TraceRootConfigImpl config =
        TraceRootConfigImpl.builder()
            .serviceName("minitasks-spring-app")
            .githubOwner("traceroot-ai")
            .githubRepoName("traceroot-sdk-java")
            .githubCommitHash("main")
            .token(System.getenv("TRACEROOT_TOKEN"))
            .environment("development")
            .awsRegion("us-west-2")
            .enableSpanConsoleExport(false)
            .enableLogConsoleExport(true)
            .enableSpanCloudExport(true)
            .enableLogCloudExport(true)
            .localMode(false)
            .logLevel(LogLevel.INFO)
            .rootPath(System.getenv("TRACEROOT_ROOT_PATH"))
            .build();

    // Initialize SDK after Spring Boot has fully started
    TraceRootSDK.initialize(config);
  }
}
