package ai.traceroot.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TencentCredentials {
  @JsonProperty("secret_id")
  private String secretId;

  @JsonProperty("secret_key")
  private String secretKey;

  @JsonProperty("session_token")
  private String sessionToken; // Optional: Only required for temporary credentials

  private String region;
  private String logset; // Equivalent to AWS Log Group
  private String topic; // Equivalent to AWS Log Stream
  private String otlpEndpoint;
  private String traceToken; // APM trace token for authentication

  public TencentCredentials() {}

  public TencentCredentials(
      String secretId,
      String secretKey,
      String sessionToken,
      String region,
      String logset,
      String topic,
      String otlpEndpoint,
      String traceToken) {
    this.secretId = secretId;
    this.secretKey = secretKey;
    this.sessionToken = sessionToken;
    this.region = region;
    this.logset = logset;
    this.topic = topic;
    this.otlpEndpoint = otlpEndpoint;
    this.traceToken = traceToken;
  }

  public String getSecretId() {
    return secretId;
  }

  public void setSecretId(String secretId) {
    this.secretId = secretId;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(String secretKey) {
    this.secretKey = secretKey;
  }

  public String getSessionToken() {
    return sessionToken;
  }

  public void setSessionToken(String sessionToken) {
    this.sessionToken = sessionToken;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getLogset() {
    return logset;
  }

  public void setLogset(String logset) {
    this.logset = logset;
  }

  public String getTopic() {
    return topic;
  }

  public void setTopic(String topic) {
    this.topic = topic;
  }

  public String getOtlpEndpoint() {
    return otlpEndpoint;
  }

  public void setOtlpEndpoint(String otlpEndpoint) {
    this.otlpEndpoint = otlpEndpoint;
  }

  public String getTraceToken() {
    return traceToken;
  }

  public void setTraceToken(String traceToken) {
    this.traceToken = traceToken;
  }
}
