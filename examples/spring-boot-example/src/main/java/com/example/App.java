package com.example;

import ai.traceroot.sdk.TraceRootSDK;
import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.types.LogLevel;
import io.github.cdimascio.dotenv.Dotenv;
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
    // Load .env file (optional - falls back to system environment variables)
    Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

    TraceRootConfigImpl config =
        TraceRootConfigImpl.builderWithEnvDefaults()
            .serviceName("minitasks-spring-app")
            .githubOwner("traceroot-ai")
            .githubRepoName("traceroot-sdk-java")
            .githubCommitHash("main")
            .token(dotenv.get("TRACEROOT_TOKEN", System.getenv("TRACEROOT_TOKEN")))
            .environment("development")
            .region("us-west-2")
            .enableSpanConsoleExport(false)
            .enableLogConsoleExport(true)
            .enableSpanCloudExport(
                dotenv.get("TRACEROOT_ENABLE_SPAN_CLOUD_EXPORT", System.getenv("TRACEROOT_ENABLE_SPAN_CLOUD_EXPORT")))
            .enableLogCloudExport(
                dotenv.get("TRACEROOT_ENABLE_LOG_CLOUD_EXPORT", System.getenv("TRACEROOT_ENABLE_LOG_CLOUD_EXPORT")))
            .localMode(false)
            .logLevel(LogLevel.INFO)
            .tracerVerbose(true)
            .rootPath(dotenv.get("TRACEROOT_ROOT_PATH", System.getenv("TRACEROOT_ROOT_PATH")))
            .build();

    // Initialize SDK after Spring Boot has fully started
    TraceRootSDK.initialize(config);
  }
}
