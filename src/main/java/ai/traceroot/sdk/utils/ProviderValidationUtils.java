package ai.traceroot.sdk.utils;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.types.Provider;

public class ProviderValidationUtils {

  public static void validateProviderCredentials(TraceRootConfigImpl config) {
    if (config.getProvider() == Provider.AWS && config.getAwsCredentials() == null) {
      System.err.println("[TraceRoot] AWS provider selected but credentials not available");
    }
  }
}
