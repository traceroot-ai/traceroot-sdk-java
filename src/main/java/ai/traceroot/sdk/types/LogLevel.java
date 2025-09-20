package ai.traceroot.sdk.types;

public enum LogLevel {
  DEBUG,
  INFO,
  WARN,
  ERROR,
  OFF;

  public static LogLevel fromString(String level) {
    if (level == null) {
      return DEBUG;
    }
    try {
      return LogLevel.valueOf(level.toUpperCase());
    } catch (IllegalArgumentException e) {
      return DEBUG;
    }
  }
}
