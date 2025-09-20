package com.traceroot.sdk.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AwsCredentials {
  @JsonProperty("aws_access_key_id")
  private String accessKeyId;

  @JsonProperty("aws_secret_access_key")
  private String secretAccessKey;

  @JsonProperty("aws_session_token")
  private String sessionToken;

  private String region;
  private String otlpEndpoint;
  private String hash;
  private Instant expirationUtc;

  public AwsCredentials() {}

  public AwsCredentials(
      String accessKeyId,
      String secretAccessKey,
      String sessionToken,
      String region,
      String otlpEndpoint,
      String hash,
      Instant expirationUtc) {
    this.accessKeyId = accessKeyId;
    this.secretAccessKey = secretAccessKey;
    this.sessionToken = sessionToken;
    this.region = region;
    this.otlpEndpoint = otlpEndpoint;
    this.hash = hash;
    this.expirationUtc = expirationUtc;
  }

  public String getAccessKeyId() {
    return accessKeyId;
  }

  public void setAccessKeyId(String accessKeyId) {
    this.accessKeyId = accessKeyId;
  }

  public String getSecretAccessKey() {
    return secretAccessKey;
  }

  public void setSecretAccessKey(String secretAccessKey) {
    this.secretAccessKey = secretAccessKey;
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

  public String getOtlpEndpoint() {
    return otlpEndpoint;
  }

  public void setOtlpEndpoint(String otlpEndpoint) {
    this.otlpEndpoint = otlpEndpoint;
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
