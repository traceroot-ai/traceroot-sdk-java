package ai.traceroot.sdk.constants;

public final class TraceRootConstants {

  public static final String TRACER_NAME = "traceroot-sdk-java";
  public static final String TELEMETRY_SDK_LANGUAGE = "java";
  public static final String TELEMETRY_SDK_LANGUAGE_UNDERSCORE = "telemetry_sdk_language";

  // API Configuration
  public static final String DEFAULT_API_BASE_URL = "https://api.test.traceroot.ai";
  public static final String CREDENTIAL_ENDPOINT = "/v1/verify/credentials";

  // HTTP Configuration
  public static final int HTTP_TIMEOUT_SECONDS = 5;
  public static final int MAX_RETRIES = 3;

  // Credential Refresh Configuration
  public static final long CREDENTIAL_REFRESH_INTERVAL_HOURS = 12;
  public static final long CREDENTIAL_REFRESH_INTERVAL_MILLIS =
      CREDENTIAL_REFRESH_INTERVAL_HOURS * 60 * 60 * 1000;

  // Batch Span Processor Configuration
  public static final int MAX_EXPORT_BATCH_SIZE = 512;
  public static final int EXPORT_TIMEOUT_MILLIS = 30000;
  public static final int SCHEDULED_DELAY_MILLIS = 1000;
  public static final int MAX_QUEUE_SIZE = 2048;

  // System Properties
  public static final String SYSTEM_PROPERTY_API_BASE_URL = "traceroot.api.baseUrl";
  public static final String SYSTEM_PROPERTY_CREDENTIAL_REFRESH_HOURS =
      "traceroot.credential.refreshHours";

  // Environment Variables
  public static final String ENV_API_BASE_URL = "TRACEROOT_API_BASE_URL";
  public static final String ENV_CREDENTIAL_REFRESH_HOURS = "TRACEROOT_CREDENTIAL_REFRESH_HOURS";

  private TraceRootConstants() {
    // Utility class
  }
}
