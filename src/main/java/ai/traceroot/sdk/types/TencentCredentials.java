package ai.traceroot.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TencentCredentials {
  @JsonProperty("secret_id")
  private String secretId;

  @JsonProperty("secret_key")
  private String secretKey;

  @JsonProperty("session_token")
  private String sessionToken;

  private String region;
  private String logset; // Equivalent to AWS Log Group
  private String topic; // Equivalent to AWS Log Stream
  private String endpoint;
  private String hash;
  private Instant expirationUtc;

  public TencentCredentials() {}

  public TencentCredentials(
      String secretId,
      String secretKey,
      String sessionToken,
      String region,
      String logset,
      String topic,
      String endpoint,
      String hash,
      Instant expirationUtc) {
    this.secretId = secretId;
    this.secretKey = secretKey;
    this.sessionToken = sessionToken;
    this.region = region;
    this.logset = logset;
    this.topic = topic;
    this.endpoint = endpoint;
    this.hash = hash;
    this.expirationUtc = expirationUtc;
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

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public String getHash() {
    return hash;
  }

  public void setHash(String hash) {
    this.hash = hash;
  }

  public Instant getExpirationUtc() {
    return expirationUtc;
  }

  public void setExpirationUtc(Instant expirationUtc) {
    this.expirationUtc = expirationUtc;
  }

  @JsonProperty("expiration_utc")
  public void setExpirationUtcFromString(String expirationUtcString) {
    if (expirationUtcString != null && !expirationUtcString.isEmpty()) {
      try {
        // Try parsing as ISO instant first
        if (expirationUtcString.endsWith("Z")) {
          this.expirationUtc = Instant.parse(expirationUtcString);
        } else {
          // Parse as local datetime and assume UTC
          LocalDateTime localDateTime = LocalDateTime.parse(expirationUtcString);
          this.expirationUtc = localDateTime.toInstant(ZoneOffset.UTC);
        }
      } catch (Exception e) {
        // If parsing fails, set to null
        this.expirationUtc = null;
      }
    }
  }

  public boolean isExpired() {
    return expirationUtc != null && Instant.now().isAfter(expirationUtc);
  }
}
