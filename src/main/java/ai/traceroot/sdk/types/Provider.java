package ai.traceroot.sdk.types;

public enum Provider {
  AWS,
  TENCENT;

  public static Provider fromString(String provider) {
    if (provider == null) {
      return AWS;
    }
    try {
      return Provider.valueOf(provider.toUpperCase());
    } catch (IllegalArgumentException e) {
      return AWS;
    }
  }
}
