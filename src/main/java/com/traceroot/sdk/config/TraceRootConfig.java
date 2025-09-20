package com.traceroot.sdk.config;

import com.traceroot.sdk.types.LogLevel;

public interface TraceRootConfig {

  // Identification
  String getServiceName();

  // GitHub Identification
  String getGithubOwner();

  String getGithubRepoName();

  String getGithubCommitHash();

  // Token for TraceRoot API
  String getToken();

  // User identification
  String getName();

  // AWS Configuration
  String getAwsRegion();

  // OpenTelemetry Configuration
  String getOtlpEndpoint();

  // Environment
  String getEnvironment();

  // Console export
  boolean isEnableSpanConsoleExport();

  boolean isEnableLogConsoleExport();

  // Cloud export
  boolean isEnableSpanCloudExport();

  boolean isEnableLogCloudExport();

  // Local mode
  boolean isLocalMode();

  // Logging configuration
  LogLevel getLogLevel();

  // Tracer verbose logging
  boolean isTracerVerbose();

  // Root path for source code (for stacktrace transformation)
  String getRootPath();

  // Internal properties (set during initialization)
  String getInternalName();

  String getInternalSubName();
}
