package ai.traceroot.sdk.utils;

import ai.traceroot.sdk.config.TraceRootConfigImpl;

public class ProviderValidationUtils {

  /**
   * Validates provider-specific credentials are available when required. AWS and Tencent
   * credentials are optional and only required when using their respective providers.
   */
  public static void validateProviderCredentials(TraceRootConfigImpl config) {
    switch (config.getProvider()) {
      case AWS:
        if (config.getAwsCredentials() == null) {
          System.err.println("[TraceRoot] AWS provider selected but credentials not available");
        }
        break;
      case TENCENT:
        if (config.getTencentCredentials() == null) {
          System.err.println("[TraceRoot] Tencent provider selected but credentials not available");
        }
        break;
      default:
        System.err.println("[TraceRoot] Unknown provider: " + config.getProvider());
        break;
    }
  }
}
