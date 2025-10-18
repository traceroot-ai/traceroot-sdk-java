package ai.traceroot.sdk.tracer;

import ai.traceroot.sdk.api.CredentialService;
import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.constants.TraceRootConstants;
import ai.traceroot.sdk.types.AwsCredentials;
import ai.traceroot.sdk.utils.CredentialRefreshScheduler;
import ai.traceroot.sdk.utils.SystemUtils;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
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
      // Try Logback first
      if (isLogbackAvailable()) {
        suppressNoisyLoggersLogback();
      } else if (isLog4j2Available()) {
        suppressNoisyLoggersLog4j2();
      }
    } catch (Exception e) {
      // Ignore errors in logger setup
    }
  }

  private void suppressNoisyLoggersLogback() {
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

  private void suppressNoisyLoggersLog4j2() {
    try {
      org.apache.logging.log4j.core.config.Configurator.setLevel(
          "org.apache.hc", org.apache.logging.log4j.Level.WARN);
      org.apache.logging.log4j.core.config.Configurator.setLevel(
          "software.amazon.awssdk", org.apache.logging.log4j.Level.WARN);
      org.apache.logging.log4j.core.config.Configurator.setLevel(
          "io.opentelemetry", org.apache.logging.log4j.Level.WARN);
    } catch (Exception e) {
      // Ignore errors in logger setup
    }
  }

  /** Prepare and validate configuration */
  private void prepareConfig() {
    // Fetch credentials based on provider if any cloud export is enabled
    if (!config.isLocalMode()
        && (config.isEnableSpanCloudExport() || config.isEnableLogCloudExport())) {

      if (config.getProvider() == ai.traceroot.sdk.types.Provider.AWS) {
        // AWS requires token for credential API
        if (config.getToken() != null) {
          prepareAwsConfig();
        }
      } else if (config.getProvider() == ai.traceroot.sdk.types.Provider.TENCENT) {
        // Tencent uses direct credentials, no token needed
        prepareTencentConfig();
      }
    }
  }

  /** Prepare AWS-specific configuration */
  private void prepareAwsConfig() {
    CredentialService credentialService = new CredentialService();
    AwsCredentials credentials = credentialService.fetchAwsCredentialsSync(config);

    if (credentials != null) {
      if (config.isTracerVerbose()) {
        logger.debug(
            "[TraceRoot] Setting AWS credentials in config: "
                + (credentials.getAccessKeyId() != null
                    ? credentials.getAccessKeyId().substring(0, 4) + "..."
                    : "null"));
      }

      config.setAwsCredentials(credentials);

      if (credentials.getHash() != null) {
        config.setInternalName(credentials.getHash());
      }

      // Only use AWS-provided OTLP endpoint if user hasn't specified a custom one
      if (credentials.getOtlpEndpoint() != null) {
        String currentEndpoint = config.getOtlpEndpoint();
        boolean isDefaultEndpoint =
            currentEndpoint == null || currentEndpoint.equals("http://localhost:4318/v1/traces");

        if (isDefaultEndpoint) {
          config.setOtlpEndpoint(credentials.getOtlpEndpoint());
          if (config.isTracerVerbose()) {
            logger.debug(
                "[TraceRoot] Using AWS-provided OTLP endpoint: {}", credentials.getOtlpEndpoint());
          }
        } else {
          if (config.isTracerVerbose()) {
            logger.debug(
                "[TraceRoot] Using user-specified OTLP endpoint: {} (ignoring AWS-provided: {})",
                currentEndpoint,
                credentials.getOtlpEndpoint());
          }
        }
      }

      if (config.isTracerVerbose()) {
        logger.debug("[TraceRoot] AWS credentials fetched successfully");
      }
    } else {
      if (config.isTracerVerbose()) {
        logger.debug("[TraceRoot] No AWS credentials returned from API");
      }
    }

    try {
      credentialService.close();
    } catch (Exception e) {
      logger.warn("[TraceRoot] Error closing credential service", e);
    }
  }

  /** Prepare Tencent-specific configuration */
  private void prepareTencentConfig() {
    // For Tencent, we use the configured credentials and construct the OTLP endpoint
    if (config.getTencentCredentials() != null) {
      String region = config.getTencentCredentials().getRegion();
      if (region == null) {
        region = TraceRootConstants.TENCENT_DEFAULT_REGION; // Default region
      }

      // Construct the endpoint based on the region
      String tencentOtlpEndpoint =
          String.format(TraceRootConstants.TENCENT_APM_ENDPOINT_PATTERN, region);

      config.getTencentCredentials().setOtlpEndpoint(tencentOtlpEndpoint);
      config.setOtlpEndpoint(tencentOtlpEndpoint);

      if (config.isTracerVerbose()) {
        logger.debug("[TraceRoot] Tencent Cloud APM endpoint configured: {}", tencentOtlpEndpoint);
      }
    } else {
      logger.error("[TraceRoot] Tencent provider selected but no Tencent credentials configured");
    }
  }

  /** Setup OpenTelemetry tracing */
  private void setupTracing() {
    // Create resource with service information
    var resourceBuilder =
        Resource.getDefault().toBuilder()
            .put("service.name", config.getServiceName())
            .put("service.version", config.getGithubCommitHash())
            .put("service.github_owner", config.getGithubOwner())
            .put("service.github_repo_name", config.getGithubRepoName())
            .put("service.environment", config.getEnvironment())
            .put("telemetry.sdk.language", TraceRootConstants.TELEMETRY_SDK_LANGUAGE);

    // Add host.name for instance identification
    String hostName = SystemUtils.getHostName();
    resourceBuilder.put("host.name", hostName);

    // Add Tencent Cloud APM token as resource attribute if provider is Tencent
    if (config.getProvider() == ai.traceroot.sdk.types.Provider.TENCENT
        && config.getTencentCredentials() != null
        && config.getTencentCredentials().getTraceToken() != null) {

      String token = config.getTencentCredentials().getTraceToken();
      resourceBuilder.put(TraceRootConstants.TENCENT_APM_TOKEN_ATTRIBUTE, token);
    }

    Resource resource = resourceBuilder.build();

    // Create tracer provider builder
    var tracerProviderBuilder = SdkTracerProvider.builder().setResource(resource);

    // Add span processors based on configuration
    if (config.isEnableSpanCloudExport()) {
      SpanExporter otlpExporter = createOtlpExporter();

      if (config.isLocalMode()) {
        tracerProviderBuilder.addSpanProcessor(SimpleSpanProcessor.create(otlpExporter));
      } else {
        // Create BatchSpanProcessor using builder pattern
        BatchSpanProcessor batchProcessor = BatchSpanProcessor.builder(otlpExporter).build();
        tracerProviderBuilder.addSpanProcessor(batchProcessor);
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

    // Add cleanup processor to prevent memory leaks in SpanLogCounter
    tracerProviderBuilder.addSpanProcessor(new SpanLogCounterCleanupProcessor());

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

  /** Create OTLP exporter with provider-specific authentication */
  private SpanExporter createOtlpExporter() {
    String endpoint = config.getOtlpEndpoint();
    SpanExporter exporter;
    if (config.getProvider() == ai.traceroot.sdk.types.Provider.TENCENT) {
      // Use gRPC exporter for Tencent Cloud APM
      var grpcBuilder = OtlpGrpcSpanExporter.builder().setEndpoint(endpoint);

      // For Tencent Cloud APM, authentication is handled via resource attributes (token)
      // No headers needed - the token is passed as a resource attribute
      if (config.getTencentCredentials() != null && config.isTracerVerbose()) {
        logger.debug("[TraceRoot] Configured Tencent Cloud APM gRPC exporter");
      }
      exporter = grpcBuilder.build();
    } else {
      // Use HTTP exporter for AWS and other providers
      var httpBuilder = OtlpHttpSpanExporter.builder().setEndpoint(endpoint);
      // For AWS, authentication is typically handled by the endpoint or AWS SDK integration
      // No additional headers needed for AWS X-Ray or other AWS tracing services
      exporter = httpBuilder.build();
    }

    return exporter;
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
      // Detect which logging backend is available and initialize accordingly (prefers Logback)
      boolean isLogbackAvailable = isLogbackAvailable();

      if (isLogbackAvailable) {
        // Use Logback backend (preferred)
        try {
          Class<?> logbackLoggerClass =
              Class.forName("ai.traceroot.sdk.logger.logback.LogbackTraceRootLogger");
          java.lang.reflect.Method initMethod =
              logbackLoggerClass.getDeclaredMethod("initialize", TraceRootConfigImpl.class);
          initMethod.invoke(null, config);

          if (config.isTracerVerbose()) {
            logger.debug("[TraceRoot] Logging setup completed with Logback backend");
          }
        } catch (Exception e) {
          logger.warn(
              "[TraceRoot] Failed to initialize Logback logger, trying Log4j2: " + e.getMessage());
          // Fall back to Log4j2 if Logback initialization fails
          setupLog4j2Logging();
        }
      } else {
        // Fall back to Log4j2 backend
        setupLog4j2Logging();
      }
    } catch (Exception e) {
      logger.error("[TraceRoot] Failed to setup logging", e);
    }
  }

  /** Setup Log4j2 logging */
  private void setupLog4j2Logging() {
    try {
      Class<?> log4j2LoggerClass =
          Class.forName("ai.traceroot.sdk.logger.log4j2.Log4j2TraceRootLogger");
      java.lang.reflect.Method initMethod =
          log4j2LoggerClass.getDeclaredMethod("initialize", TraceRootConfigImpl.class);
      initMethod.invoke(null, config);

      if (config.isTracerVerbose()) {
        logger.debug("[TraceRoot] Logging setup completed with Log4j2 backend");
      }
    } catch (Exception e) {
      logger.error("[TraceRoot] Failed to initialize Log4j2 logger: " + e.getMessage());
      throw new RuntimeException("No supported logging backend found", e);
    }
  }

  /** Check if Logback is available on the classpath */
  private boolean isLogbackAvailable() {
    try {
      Class.forName("ch.qos.logback.classic.Logger");
      Class.forName("ai.traceroot.sdk.logger.logback.LogbackTraceRootLogger");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  /** Check if Log4j2 is available on the classpath */
  private boolean isLog4j2Available() {
    try {
      Class.forName("org.apache.logging.log4j.core.Logger");
      Class.forName("ai.traceroot.sdk.logger.log4j2.Log4j2TraceRootLogger");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  /** Shutdown logging appenders */
  private void shutdownLogging() {
    try {
      // Detect which logging backend is being used and shutdown accordingly (prefers Logback)
      boolean isLogbackAvailable = isLogbackAvailable();

      if (isLogbackAvailable) {
        try {
          Class<?> logbackLoggerClass =
              Class.forName("ai.traceroot.sdk.logger.logback.LogbackTraceRootLogger");
          java.lang.reflect.Method shutdownMethod =
              logbackLoggerClass.getDeclaredMethod("shutdown");
          shutdownMethod.invoke(null);
        } catch (Exception e) {
          logger.warn("[TraceRoot] Failed to shutdown Logback logger: " + e.getMessage());
          e.printStackTrace();
        }
      } else if (isLog4j2Available()) {
        try {
          Class<?> log4j2LoggerClass =
              Class.forName("ai.traceroot.sdk.logger.log4j2.Log4j2TraceRootLogger");
          java.lang.reflect.Method shutdownMethod = log4j2LoggerClass.getDeclaredMethod("shutdown");
          shutdownMethod.invoke(null);
        } catch (Exception e) {
          logger.warn("[TraceRoot] Failed to shutdown Log4j2 logger: " + e.getMessage());
          e.printStackTrace();
        }
      }

      if (config.isTracerVerbose()) {
        logger.debug("[TraceRoot] Logging shutdown completed");
      }
    } catch (Exception e) {
      logger.error("[TraceRoot] Error shutting down logging", e);
    }
  }
}
