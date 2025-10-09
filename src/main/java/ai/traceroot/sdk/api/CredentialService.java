package ai.traceroot.sdk.api;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.constants.TraceRootConstants;
import ai.traceroot.sdk.types.AwsCredentials;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CredentialService {
  private static final Logger logger = LoggerFactory.getLogger(CredentialService.class);
  private static final ObjectMapper objectMapper =
      new ObjectMapper()
          .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
          .registerModule(new JavaTimeModule())
          .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);

  private final String apiBaseUrl;
  private final CloseableHttpClient httpClient;

  public CredentialService() {
    this.apiBaseUrl = getTraceRootApiBaseUrl();

    RequestConfig requestConfig =
        RequestConfig.custom()
            .setResponseTimeout(Timeout.ofSeconds(TraceRootConstants.HTTP_TIMEOUT_SECONDS))
            .setConnectionRequestTimeout(Timeout.ofSeconds(TraceRootConstants.HTTP_TIMEOUT_SECONDS))
            .build();

    this.httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build();
  }

  public CredentialService(String apiBaseUrl) {
    this.apiBaseUrl = apiBaseUrl;

    RequestConfig requestConfig =
        RequestConfig.custom()
            .setResponseTimeout(Timeout.ofSeconds(TraceRootConstants.HTTP_TIMEOUT_SECONDS))
            .setConnectionRequestTimeout(Timeout.ofSeconds(TraceRootConstants.HTTP_TIMEOUT_SECONDS))
            .build();

    this.httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build();
  }

  /**
   * Fetch AWS credentials from TraceRoot API (synchronous) This method is synchronous because
   * credentials are needed during initialization
   */
  public AwsCredentials fetchAwsCredentialsSync(TraceRootConfigImpl config) {
    if (config.getToken() == null || config.getToken().trim().isEmpty()) {
      if (config.isTracerVerbose()) {
        logger.debug("[TraceRoot] No token provided for credential fetch");
      }
      return null;
    }

    try {
      String encodedToken = URLEncoder.encode(config.getToken(), StandardCharsets.UTF_8);
      String apiUrl =
          apiBaseUrl + TraceRootConstants.CREDENTIAL_ENDPOINT + "?token=" + encodedToken;

      if (config.isTracerVerbose()) {
        logger.debug("[TraceRoot] Fetching credentials from: {}", apiUrl);
      }

      HttpGet request = new HttpGet(apiUrl);
      request.setHeader("Content-Type", "application/json");
      request.setHeader("User-Agent", "TraceRoot-SDK-Java");

      try (CloseableHttpClient client = this.httpClient) {
        ClassicHttpResponse response = client.execute(request);

        int statusCode = response.getCode();
        String responseBody = EntityUtils.toString(response.getEntity());

        if (statusCode >= 200 && statusCode < 300) {
          AwsCredentials credentials = objectMapper.readValue(responseBody, AwsCredentials.class);

          if (config.isTracerVerbose()) {
            logger.debug(
                "[TraceRoot] Credentials fetched successfully for token: {}... → {}",
                config.getToken().substring(0, Math.min(20, config.getToken().length())),
                credentials.getOtlpEndpoint());
          }

          return credentials;
        } else {
          if (config.isTracerVerbose()) {
            logger.warn(
                "[TraceRoot] Failed to fetch credentials. Status: {}, Response: {}",
                statusCode,
                responseBody);
          }
          return null;
        }
      }
    } catch (IOException e) {
      if (config.isTracerVerbose()) {
        logger.error("[TraceRoot] Error fetching credentials: {}", e.getMessage());
      }
      return null;
    } catch (Exception e) {
      if (config.isTracerVerbose()) {
        logger.error("[TraceRoot] Unexpected error fetching credentials: {}", e.getMessage());
      }
      return null;
    }
  }

  /** Check if credentials need refresh (expired or will expire soon) */
  public boolean needsRefresh(AwsCredentials credentials) {
    if (credentials == null || credentials.getExpirationUtc() == null) {
      return true;
    }

    // Refresh if expired or expiring within 1 hour
    Instant refreshThreshold = Instant.now().plusSeconds(3600);
    return credentials.getExpirationUtc().isBefore(refreshThreshold);
  }

  /**
   * Get the TraceRoot API base URL - can be overridden via system property or environment variable
   */
  private String getTraceRootApiBaseUrl() {
    // Check system property first
    String url = System.getProperty(TraceRootConstants.SYSTEM_PROPERTY_API_BASE_URL);
    if (url != null && !url.trim().isEmpty()) {
      return url.trim();
    }

    // Check environment variable
    url = System.getenv(TraceRootConstants.ENV_API_BASE_URL);
    if (url != null && !url.trim().isEmpty()) {
      return url.trim();
    }

    // Default URL
    return TraceRootConstants.DEFAULT_API_BASE_URL;
  }

  public void close() throws IOException {
    if (httpClient != null) {
      httpClient.close();
    }
  }
}
