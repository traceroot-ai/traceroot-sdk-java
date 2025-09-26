package ai.traceroot.sdk.utils;

import ai.traceroot.sdk.config.TraceRootConfigImpl;

public class ProviderValidationUtils {

  /**
   * Validates provider-specific credentials are available when required. AWS credentials are
   * optional and only required when using AWS provider. Other providers may have their own
   * credential requirements in the future.
   */
  public static void validateProviderCredentials(TraceRootConfigImpl config) {
    switch (config.getProvider()) {
      case AWS:
        if (config.getAwsCredentials() == null) {
          System.err.println("[TraceRoot] AWS provider selected but credentials not available");
        }
        break;
      case TENCENT:
        // TODO: Add Tencent credential validation when implemented
        break;
      default:
        System.err.println("[TraceRoot] Unknown provider: " + config.getProvider());
        break;
    }
  }
}
