package ai.traceroot.sdk.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SystemUtils {

  private static final Logger logger = LoggerFactory.getLogger(SystemUtils.class);

  /**
   * Get hostname with fallback mechanisms for containerized environments.
   *
   * <p>This method tries multiple approaches to get the hostname:
   *
   * <ul>
   *   <li>Environment variable HOSTNAME (common in containers)
   *   <li>System property "hostname"
   *   <li>InetAddress.getLocalHost().getHostName() (may be slow in containers)
   *   <li>Default fallback value "unknown-host"
   * </ul>
   *
   * @return The hostname, or "unknown-host" if none can be determined
   */
  public static String getHostName() {
    // Try environment variable first (common in containers)
    String hostName = System.getenv("HOSTNAME");
    if (hostName != null && !hostName.trim().isEmpty()) {
      return hostName.trim();
    }

    // Try system property as fallback
    hostName = System.getProperty("hostname");
    if (hostName != null && !hostName.trim().isEmpty()) {
      return hostName.trim();
    }

    // Last resort: use InetAddress (may be slow in containers)
    try {
      hostName = java.net.InetAddress.getLocalHost().getHostName();
      if (hostName != null && !hostName.trim().isEmpty()) {
        return hostName.trim();
      }
    } catch (Exception e) {
      logger.warn("[TraceRoot] Failed to get hostname via InetAddress: {}", e.getMessage());
    }

    // Default fallback
    logger.warn("[TraceRoot] Unable to determine hostname, using default value");
    return "unknown-host";
  }

  private SystemUtils() {
    // Utility class
  }
}
