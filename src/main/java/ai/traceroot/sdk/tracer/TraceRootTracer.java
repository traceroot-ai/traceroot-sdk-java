package ai.traceroot.sdk.tracer;

import ai.traceroot.sdk.api.CredentialService;
import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.constants.TraceRootConstants;
import ai.traceroot.sdk.logger.TraceRootLogger;
import ai.traceroot.sdk.types.AwsCredentials;
import ai.traceroot.sdk.utils.CredentialRefreshScheduler;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Main tracer class for TraceRoot SDK */
public class TraceRootTracer {
  private static final Logger logger = LoggerFactory.getLogger(TraceRootTracer.class);

  private static volatile TraceRootTracer instance;
  private static final AtomicBoolean initialized = new AtomicBoolean(false);

  private TraceRootConfigImpl config;
  private OpenTelemetry openTelemetry;
  private Tracer tracer;
  private CredentialRefreshScheduler credentialScheduler;
  private SdkTracerProvider tracerProvider;

  private TraceRootTracer() {}

  /** Initialize TraceRoot tracing (synchronous) */
  public static TraceRootTracer initialize(TraceRootConfigImpl config) {
    if (initialized.get()) {
      logger.warn("[TraceRoot] Tracer already initialized");
      return instance;
    }

    synchronized (TraceRootTracer.class) {
      if (initialized.get()) {
        return instance;
      }

      instance = new TraceRootTracer();
      instance.config = config;

      try {
        // First suppress noisy loggers before making any HTTP requests
        instance.suppressNoisyLoggers();

        // Then prepare config (this fetches AWS credentials)
        instance.prepareConfig();

        // Then setup tracing and logging (both need credentials)
        instance.setupTracing();
        instance.setupLogging();

        // Finally start credential scheduler
        instance.startCredentialScheduler();

        initialized.set(true);

        if (config.isTracerVerbose()) {
          logger.info("[TraceRoot] Tracer initialized successfully");
        }
      } catch (Exception e) {
        logger.error("[TraceRoot] Failed to initialize tracer", e);
        throw new RuntimeException("Failed to initialize TraceRoot tracer", e);
      }
    }

    return instance;
  }

  /** Get the singleton instance */
  public static TraceRootTracer getInstance() {
    if (!initialized.get()) {
      throw new IllegalStateException("TraceRoot tracer not initialized. Call initialize() first.");
    }
    return instance;
  }

  /** Check if tracer is initialized */
  public static boolean isInitialized() {
    return initialized.get();
  }

  /** Get the OpenTelemetry tracer */
  public Tracer getTracer() {
    return tracer;
  }

  /** Get the OpenTelemetry instance */
  public OpenTelemetry getOpenTelemetry() {
    return openTelemetry;
  }

  /** Get the current configuration */
  public TraceRootConfigImpl getConfig() {
    return config;
  }

  /** Force flush all pending spans */
  public void forceFlush() {
    if (tracerProvider != null) {
      try {
        tracerProvider.forceFlush().join(30, TimeUnit.SECONDS);
        if (config.isTracerVerbose()) {
          logger.debug("[TraceRoot] Tracer flushed successfully");
        }
      } catch (Exception e) {
        logger.error("[TraceRoot] Error flushing tracer", e);
      }
    }
  }

  /** Shutdown tracing and flush any pending spans */
  public void shutdown() {
    if (!initialized.get()) {
      return;
    }

    synchronized (TraceRootTracer.class) {
      if (!initialized.get()) {
        return;
      }

      try {
        // Stop credential scheduler
        if (credentialScheduler != null) {
          credentialScheduler.shutdown();
        }

        // Shutdown tracer provider
        if (tracerProvider != null) {
          tracerProvider.shutdown().join(10, TimeUnit.SECONDS);
        }

        // Shutdown logging appenders
        shutdownLogging();

        initialized.set(false);
        instance = null;

        if (config.isTracerVerbose()) {
          logger.info("[TraceRoot] Tracer shutdown completed");
        }
      } catch (Exception e) {
        logger.error("[TraceRoot] Error during tracer shutdown", e);
      }
    }
  }

  /** Suppress noisy loggers before making HTTP requests */
  private void suppressNoisyLoggers() {
    try {
      ch.qos.logback.classic.LoggerContext context =
          (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();

      // Suppress Apache HTTP Client logs
      ch.qos.logback.classic.Logger httpClientLogger = context.getLogger("org.apache.hc");
      httpClientLogger.setLevel(ch.qos.logback.classic.Level.WARN);

      // Suppress AWS SDK logs
      ch.qos.logback.classic.Logger awsSdkLogger = context.getLogger("software.amazon.awssdk");
      awsSdkLogger.setLevel(ch.qos.logback.classic.Level.WARN);

      // Suppress OpenTelemetry logs
      ch.qos.logback.classic.Logger otlpLogger = context.getLogger("io.opentelemetry");
      otlpLogger.setLevel(ch.qos.logback.classic.Level.WARN);
    } catch (Exception e) {
      // Ignore errors in logger setup
    }
  }

  /** Prepare and validate configuration */
  private void prepareConfig() {
    // Fetch AWS credentials if any cloud export is enabled
    if (!config.isLocalMode()
        && (config.isEnableSpanCloudExport() || config.isEnableLogCloudExport())
        && config.getToken() != null) {
      CredentialService credentialService = new CredentialService();
      AwsCredentials credentials = credentialService.fetchAwsCredentialsSync(config);

      if (credentials != null) {
        if (config.isTracerVerbose()) {
          System.out.println(
              "[TraceRoot] Setting AWS credentials in config: "
                  + (credentials.getAccessKeyId() != null
                      ? credentials.getAccessKeyId().substring(0, 4) + "..."
                      : "null"));
        }

        config.setAwsCredentials(credentials);

        if (credentials.getHash() != null) {
          config.setInternalName(credentials.getHash());
        }

        if (credentials.getOtlpEndpoint() != null) {
          config.setOtlpEndpoint(credentials.getOtlpEndpoint());
        }

        if (config.isTracerVerbose()) {
          logger.debug(
              "[TraceRoot] Credentials fetched successfully for token: {}... → {}",
              config.getToken().substring(0, Math.min(20, config.getToken().length())),
              credentials.getOtlpEndpoint());
        }
      } else {
        if (config.isTracerVerbose()) {
          System.out.println("[TraceRoot] No credentials returned from API");
        }
      }

      try {
        credentialService.close();
      } catch (Exception e) {
        logger.warn("[TraceRoot] Error closing credential service", e);
      }
    }
  }

  /** Setup OpenTelemetry tracing */
  private void setupTracing() {
    // Create resource with service information
    Resource resource =
        Resource.getDefault().toBuilder()
            .put("service.name", config.getServiceName())
            .put("service.version", config.getGithubCommitHash())
            .put("service.github_owner", config.getGithubOwner())
            .put("service.github_repo_name", config.getGithubRepoName())
            .put("service.environment", config.getEnvironment())
            .put("telemetry.sdk.language", TraceRootConstants.TELEMETRY_SDK_LANGUAGE)
            .build();

    // Create tracer provider builder
    var tracerProviderBuilder = SdkTracerProvider.builder().setResource(resource);

    // Add span processors based on configuration
    if (config.isEnableSpanCloudExport()) {
      SpanExporter otlpExporter =
          OtlpHttpSpanExporter.builder().setEndpoint(config.getOtlpEndpoint()).build();

      if (config.isLocalMode()) {
        tracerProviderBuilder.addSpanProcessor(SimpleSpanProcessor.create(otlpExporter));
      } else {
        // Create BatchSpanProcessor using builder pattern
        tracerProviderBuilder.addSpanProcessor(BatchSpanProcessor.builder(otlpExporter).build());
      }

      if (config.isTracerVerbose()) {
        logger.debug("[TraceRoot] Created OTLP exporter for: {}", config.getOtlpEndpoint());
      }
    }

    // Add console export if enabled
    if (config.isEnableSpanConsoleExport()) {
      SpanExporter loggingExporter = LoggingSpanExporter.create();
      tracerProviderBuilder.addSpanProcessor(SimpleSpanProcessor.create(loggingExporter));

      if (config.isTracerVerbose()) {
        logger.debug("[TraceRoot] Console span export enabled");
      }
    }

    // Build tracer provider
    tracerProvider = tracerProviderBuilder.build();

    // Build OpenTelemetry SDK
    openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();

    // Set as global (if not already set)
    try {
      GlobalOpenTelemetry.set(openTelemetry);
    } catch (IllegalStateException e) {
      // Global OpenTelemetry already set, use existing one
      openTelemetry = GlobalOpenTelemetry.get();
      if (config.isTracerVerbose()) {
        logger.debug("[TraceRoot] Using existing global OpenTelemetry instance");
      }
    }

    // Get tracer
    tracer = openTelemetry.getTracer(TraceRootConstants.TRACER_NAME);

    if (config.isTracerVerbose()) {
      logger.debug("[TraceRoot] OpenTelemetry tracer setup completed");
    }
  }

  /** Start credential refresh scheduler */
  private void startCredentialScheduler() {
    if (!config.isLocalMode()
        && (config.isEnableSpanCloudExport() || config.isEnableLogCloudExport())
        && config.getToken() != null) {

      credentialScheduler = new CredentialRefreshScheduler(config, this::onCredentialsRefreshed);
      credentialScheduler.start();

      if (config.isTracerVerbose()) {
        logger.debug("[TraceRoot] Credential refresh scheduler started");
      }
    }
  }

  /** Handle credential refresh */
  private void onCredentialsRefreshed(AwsCredentials newCredentials) {
    if (config.isTracerVerbose()) {
      logger.debug("[TraceRoot] Credentials refreshed, updating configuration");
    }

    // Update configuration with new credentials
    config.setAwsCredentials(newCredentials);

    // Update OTLP endpoint if changed
    if (newCredentials.getOtlpEndpoint() != null
        && !newCredentials.getOtlpEndpoint().equals(config.getOtlpEndpoint())) {

      config.setOtlpEndpoint(newCredentials.getOtlpEndpoint());

      if (config.isTracerVerbose()) {
        logger.debug("[TraceRoot] OTLP endpoint updated to: {}", newCredentials.getOtlpEndpoint());
      }

      // Note: In a production implementation, you might want to recreate the
      // OTLP exporter with the new endpoint. For now, we just update the config.
    }
  }

  /** Setup TraceRoot logging with CloudWatch integration */
  private void setupLogging() {
    try {
      // Initialize TraceRoot logger with CloudWatch integration
      TraceRootLogger.initialize(config);

      if (config.isTracerVerbose()) {
        logger.debug("[TraceRoot] Logging setup completed");
      }
    } catch (Exception e) {
      logger.error("[TraceRoot] Failed to setup logging", e);
    }
  }

  /** Shutdown logging appenders */
  private void shutdownLogging() {
    try {
      TraceRootLogger.shutdown();

      if (config.isTracerVerbose()) {
        logger.debug("[TraceRoot] Logging shutdown completed");
      }
    } catch (Exception e) {
      logger.error("[TraceRoot] Error shutting down logging", e);
    }
  }
}
