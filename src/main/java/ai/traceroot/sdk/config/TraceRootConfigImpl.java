package ai.traceroot.sdk.config;

import ai.traceroot.sdk.constants.TraceRootConstants;
import ai.traceroot.sdk.types.AwsCredentials;
import ai.traceroot.sdk.types.LogLevel;
import ai.traceroot.sdk.types.Provider;

public class TraceRootConfigImpl implements TraceRootConfig {
  private String serviceName;
  private String githubOwner;
  private String githubRepoName;
  private String githubCommitHash;
  private String token;
  private String name;
  private Provider provider = Provider.AWS;
  private String awsRegion = "us-west-2";
  private String otlpEndpoint = "http://localhost:4318/v1/traces";
  private String environment = "development";
  private boolean enableSpanConsoleExport = false;
  private boolean enableLogConsoleExport = true;
  private boolean enableSpanCloudExport = false;
  private boolean enableLogCloudExport = false;
  private boolean localMode = false;
  private LogLevel logLevel = LogLevel.DEBUG;
  private boolean tracerVerbose = false;
  private String rootPath;

  // Internal properties (set during initialization)
  private String internalName;
  private String internalSubName;
  private AwsCredentials awsCredentials;

  public TraceRootConfigImpl() {}

  public TraceRootConfigImpl(TraceRootConfig config) {
    this.serviceName =
        config.getServiceName() != null ? config.getServiceName() : "default-service";
    this.githubOwner = config.getGithubOwner() != null ? config.getGithubOwner() : "unknown";
    this.githubRepoName =
        config.getGithubRepoName() != null ? config.getGithubRepoName() : "unknown";
    this.githubCommitHash =
        config.getGithubCommitHash() != null ? config.getGithubCommitHash() : "unknown";
    this.token = config.getToken();
    this.name = config.getName();
    this.provider = config.getProvider() != null ? config.getProvider() : this.provider;
    this.awsRegion = config.getAwsRegion() != null ? config.getAwsRegion() : this.awsRegion;
    this.otlpEndpoint =
        config.getOtlpEndpoint() != null ? config.getOtlpEndpoint() : this.otlpEndpoint;
    this.environment = config.getEnvironment() != null ? config.getEnvironment() : this.environment;
    this.enableSpanConsoleExport = config.isEnableSpanConsoleExport();
    this.enableLogConsoleExport = config.isEnableLogConsoleExport();
    this.enableSpanCloudExport = config.isEnableSpanCloudExport();
    this.enableLogCloudExport = config.isEnableLogCloudExport();
    this.localMode = config.isLocalMode();
    this.logLevel = config.getLogLevel() != null ? config.getLogLevel() : this.logLevel;
    this.tracerVerbose = config.isTracerVerbose();
    this.rootPath = config.getRootPath();

    this.internalName = this.name;
    this.internalSubName = this.serviceName + "-" + this.environment;
  }

  // Builder pattern for easy configuration
  public static Builder builder() {
    return new Builder();
  }

  // Builder with environment variable defaults
  public static Builder builderWithEnvDefaults() {
    Builder builder = new Builder();

    // Set provider from environment variable
    String providerEnv = System.getenv(TraceRootConstants.ENV_PROVIDER);
    if (providerEnv != null) {
      builder.provider(Provider.fromString(providerEnv));
    }

    return builder;
  }

  public static class Builder {
    private final TraceRootConfigImpl config = new TraceRootConfigImpl();

    public Builder serviceName(String serviceName) {
      config.serviceName = serviceName;
      return this;
    }

    public Builder githubOwner(String githubOwner) {
      config.githubOwner = githubOwner;
      return this;
    }

    public Builder githubRepoName(String githubRepoName) {
      config.githubRepoName = githubRepoName;
      return this;
    }

    public Builder githubCommitHash(String githubCommitHash) {
      config.githubCommitHash = githubCommitHash;
      return this;
    }

    public Builder token(String token) {
      config.token = token;
      return this;
    }

    public Builder name(String name) {
      config.name = name;
      return this;
    }

    public Builder provider(Provider provider) {
      config.provider = provider;
      return this;
    }

    public Builder awsRegion(String awsRegion) {
      config.awsRegion = awsRegion;
      return this;
    }

    public Builder otlpEndpoint(String otlpEndpoint) {
      config.otlpEndpoint = otlpEndpoint;
      return this;
    }

    public Builder environment(String environment) {
      config.environment = environment;
      return this;
    }

    public Builder enableSpanConsoleExport(boolean enableSpanConsoleExport) {
      config.enableSpanConsoleExport = enableSpanConsoleExport;
      return this;
    }

    public Builder enableLogConsoleExport(boolean enableLogConsoleExport) {
      config.enableLogConsoleExport = enableLogConsoleExport;
      return this;
    }

    public Builder enableSpanCloudExport(boolean enableSpanCloudExport) {
      config.enableSpanCloudExport = enableSpanCloudExport;
      return this;
    }

    public Builder enableLogCloudExport(boolean enableLogCloudExport) {
      config.enableLogCloudExport = enableLogCloudExport;
      return this;
    }

    public Builder localMode(boolean localMode) {
      config.localMode = localMode;
      return this;
    }

    public Builder logLevel(LogLevel logLevel) {
      config.logLevel = logLevel;
      return this;
    }

    public Builder tracerVerbose(boolean tracerVerbose) {
      config.tracerVerbose = tracerVerbose;
      return this;
    }

    public Builder rootPath(String rootPath) {
      config.rootPath = rootPath;
      return this;
    }

    public TraceRootConfigImpl build() {
      config.internalName = config.name;
      config.internalSubName = config.serviceName + "-" + config.environment;
      return config;
    }
  }

  // Getters and setters
  @Override
  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  @Override
  public String getGithubOwner() {
    return githubOwner;
  }

  public void setGithubOwner(String githubOwner) {
    this.githubOwner = githubOwner;
  }

  @Override
  public String getGithubRepoName() {
    return githubRepoName;
  }

  public void setGithubRepoName(String githubRepoName) {
    this.githubRepoName = githubRepoName;
  }

  @Override
  public String getGithubCommitHash() {
    return githubCommitHash;
  }

  public void setGithubCommitHash(String githubCommitHash) {
    this.githubCommitHash = githubCommitHash;
  }

  @Override
  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public Provider getProvider() {
    return provider;
  }

  public void setProvider(Provider provider) {
    this.provider = provider;
  }

  @Override
  public String getAwsRegion() {
    return awsRegion;
  }

  public void setAwsRegion(String awsRegion) {
    this.awsRegion = awsRegion;
  }

  @Override
  public String getOtlpEndpoint() {
    return otlpEndpoint;
  }

  public void setOtlpEndpoint(String otlpEndpoint) {
    this.otlpEndpoint = otlpEndpoint;
  }

  @Override
  public String getEnvironment() {
    return environment;
  }

  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  @Override
  public boolean isEnableSpanConsoleExport() {
    return enableSpanConsoleExport;
  }

  public void setEnableSpanConsoleExport(boolean enableSpanConsoleExport) {
    this.enableSpanConsoleExport = enableSpanConsoleExport;
  }

  @Override
  public boolean isEnableLogConsoleExport() {
    return enableLogConsoleExport;
  }

  public void setEnableLogConsoleExport(boolean enableLogConsoleExport) {
    this.enableLogConsoleExport = enableLogConsoleExport;
  }

  @Override
  public boolean isEnableSpanCloudExport() {
    return enableSpanCloudExport;
  }

  public void setEnableSpanCloudExport(boolean enableSpanCloudExport) {
    this.enableSpanCloudExport = enableSpanCloudExport;
  }

  @Override
  public boolean isEnableLogCloudExport() {
    return enableLogCloudExport;
  }

  public void setEnableLogCloudExport(boolean enableLogCloudExport) {
    this.enableLogCloudExport = enableLogCloudExport;
  }

  @Override
  public boolean isLocalMode() {
    return localMode;
  }

  public void setLocalMode(boolean localMode) {
    this.localMode = localMode;
  }

  @Override
  public LogLevel getLogLevel() {
    return logLevel;
  }

  public void setLogLevel(LogLevel logLevel) {
    this.logLevel = logLevel;
  }

  @Override
  public boolean isTracerVerbose() {
    return tracerVerbose;
  }

  public void setTracerVerbose(boolean tracerVerbose) {
    this.tracerVerbose = tracerVerbose;
  }

  @Override
  public String getRootPath() {
    return rootPath;
  }

  public void setRootPath(String rootPath) {
    this.rootPath = rootPath;
  }

  @Override
  public String getInternalName() {
    return internalName;
  }

  public void setInternalName(String internalName) {
    this.internalName = internalName;
  }

  @Override
  public String getInternalSubName() {
    return internalSubName;
  }

  public void setInternalSubName(String internalSubName) {
    this.internalSubName = internalSubName;
  }

  public AwsCredentials getAwsCredentials() {
    return awsCredentials;
  }

  public void setAwsCredentials(AwsCredentials awsCredentials) {
    this.awsCredentials = awsCredentials;
  }
}
