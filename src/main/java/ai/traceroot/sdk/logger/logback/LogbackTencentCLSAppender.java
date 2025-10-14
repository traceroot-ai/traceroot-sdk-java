package ai.traceroot.sdk.logger.logback;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.types.TencentCredentials;
import ai.traceroot.sdk.utils.LogAppenderUtils;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.tencentcloudapi.cls.producer.AsyncProducerClient;
import com.tencentcloudapi.cls.producer.AsyncProducerConfig;
import com.tencentcloudapi.cls.producer.common.LogContent;
import com.tencentcloudapi.cls.producer.common.LogItem;
import com.tencentcloudapi.cls.v20201016.ClsClient;
import com.tencentcloudapi.cls.v20201016.models.*;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom Logback appender for sending logs to Tencent Cloud CLS with trace correlation using
 * official SDK
 */
public class LogbackTencentCLSAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

  private String logset; // Equivalent to AWS Log Group (not used in CLS SDK)
  private String topic; // Equivalent to AWS Log Stream - this will be the topic ID
  private String region = "ap-hongkong";

  /*
   * MEMORY SAFETY: Bounded Queue Protection
   *
   * RISK: ConcurrentLinkedQueue is unbounded by default. In high-throughput scenarios
   * where log production rate exceeds processing rate, the queue can grow infinitely
   * and cause OutOfMemoryError.
   *
   * SCENARIOS THAT CAUSE UNBOUNDED GROWTH:
   * 1. Network issues preventing log delivery to Tencent CLS
   * 2. Application producing logs faster than 2-second batch intervals
   * 3. Tencent CLS service unavailability or rate limiting
   * 4. Configuration errors preventing successful log transmission
   *
   * OUR SOLUTION: Implement queue size limit with graceful degradation
   */
  private int maxQueueSize = 10000; // Prevent unbounded queue growth in high-throughput scenarios
  private static final int ERROR_THROTTLE_INTERVAL = 1000; // Log every 1000 drops to avoid spam

  // Internal state
  private AsyncProducerClient clsClient;
  private TraceRootConfigImpl config;

  /*
   * MEMORY SAFETY: Queue Design
   * - ConcurrentLinkedQueue for thread-safe operations
   * - Size monitoring to prevent unbounded growth
   * - Graceful log dropping when capacity exceeded
   * - Metrics tracking for monitoring queue health
   */
  private final Queue<LogItem> logItemQueue = new ConcurrentLinkedQueue<>();
  private ScheduledExecutorService scheduler;
  private final AtomicLong processedLogs = new AtomicLong(0);
  private final AtomicLong droppedLogs =
      new AtomicLong(0); // MEMORY SAFETY: Track dropped logs for monitoring

  @Override
  public void start() {

    if (config == null) {
      // Config not set yet - appender will be configured programmatically later
      // Don't call addError() as it causes Spring Boot startup to fail
      return;
    }

    if (!config.isEnableLogCloudExport()) {
      String info = "Tencent CLS logging disabled - appender will not start";
      addInfo(info);
      return;
    }

    try {
      initializeCLSClient();
      startBatchProcessor();

      super.start();
      String success =
          "Tencent CLS appender started successfully with topic=" + topic + ", region=" + region;
      addInfo(success);
    } catch (Exception e) {
      String error = "Failed to start Tencent CLS appender: " + e.getMessage();
      addError(error, e);
      System.err.println("[TraceRoot] " + error);
    }
  }

  @Override
  public void stop() {
    LogAppenderUtils.shutdownScheduler(scheduler, 10);

    // Flush remaining logs
    flushLogs();

    // Close CLS client
    if (clsClient != null) {
      try {
        clsClient.close();
      } catch (Exception e) {
        System.err.println("[TraceRoot] Error closing CLS client: " + e.getMessage());
      }
    }

    super.stop();
    addInfo("Tencent CLS appender stopped");
  }

  @Override
  protected void append(ILoggingEvent event) {
    if (!isStarted()) {
      return;
    }
    if (config == null || !config.isEnableLogCloudExport()) {
      return;
    }

    /*
     * MEMORY SAFETY: Immediate Processing Strategy
     *
     * WHY PROCESS IMMEDIATELY:
     * The original design queued ILoggingEvent objects and processed them later
     * on a background thread. This caused issues because:
     *
     * 1. MDC (ThreadLocal) values were lost when processing on different thread
     * 2. Stack traces showed shutdown methods instead of actual user code
     * 3. Thread context was invalid by the time logs were processed
     *
     * OUR SOLUTION:
     * - Process log events immediately in append() on the original thread
     * - Queue pre-processed LogItem objects instead of raw events
     * - Preserve all context (stack trace, MDC) at the correct time
     * - Separate processing (immediate) from transmission (batched)
     */

    // Process the event immediately to capture stack trace while MDC is available
    try {
      // MEMORY SAFETY: Process immediately while thread context is valid
      LogItem logItem = createLogItem(event);

      /*
       * MEMORY SAFETY: Queue Overflow Protection
       *
       * Without this check, the queue could grow unbounded in scenarios like:
       * - Network connectivity issues
       * - Tencent CLS service outages
       * - Rate limiting from cloud provider
       * - Application generating logs faster than network can handle
       *
       * GRACEFUL DEGRADATION STRATEGY:
       * - Monitor queue size on every append
       * - Drop new logs when limit exceeded (preserve application performance)
       * - Track dropped count for monitoring/alerting
       * - Throttled error logging to avoid log spam
       */
      if (logItemQueue.size() >= maxQueueSize) {
        droppedLogs.incrementAndGet();
        // MEMORY SAFETY: Throttled error reporting to prevent log spam during high load
        if (droppedLogs.get() % ERROR_THROTTLE_INTERVAL == 1) {
          System.err.println(
              "[TraceRoot] Dropped "
                  + droppedLogs.get()
                  + " logs due to queue overflow. "
                  + "Consider increasing batch processing rate or reducing log volume.");
        }
        return; // Drop this log to prevent memory exhaustion
      }

      // MEMORY SAFETY: Queue bounded LogItem objects (processed, fixed-size) not raw events
      logItemQueue.offer(logItem);
    } catch (Exception e) {
      // MEMORY SAFETY: Error handling without rethrowing to prevent app disruption
      System.err.println("[TraceRoot] Error creating log item: " + e.getMessage());
    }
  }

  private void initializeCLSClient() throws Exception {
    TencentCredentials credentials = config.getTencentCredentials();
    if (credentials == null) {
      throw new IllegalStateException("Tencent credentials not available");
    }

    if (credentials.getSecretId() == null || credentials.getSecretKey() == null) {
      throw new IllegalStateException("Tencent credentials have null secret ID or secret key");
    }

    // Ensure topic exists (create if necessary)
    String actualTopicId = ensureTopicExists(credentials);

    // Build endpoint based on region
    String endpoint =
        String.format(
            "%s.cls.tencentcs.com",
            credentials.getRegion() != null ? credentials.getRegion() : region);

    // Create AsyncProducerConfig
    AsyncProducerConfig producerConfig =
        new AsyncProducerConfig(
            endpoint,
            credentials.getSecretId(),
            credentials.getSecretKey(),
            "TraceRootSDK" // source identifier
            );

    // Configure the producer
    producerConfig.setBatchSizeThresholdInBytes(512 * 1024); // 512KB
    producerConfig.setBatchCountThreshold(100);
    producerConfig.setLingerMs(2000); // 2 second linger time

    // Create the CLS client
    this.clsClient = new AsyncProducerClient(producerConfig);

    // Update the topic with actual topic ID
    if (credentials.getTopic() == null) {
      credentials.setTopic(actualTopicId);
    } else {
    }
  }

  private String ensureTopicExists(TencentCredentials credentials) throws Exception {
    String currentRegion = credentials.getRegion() != null ? credentials.getRegion() : region;

    // Create CLS management client
    Credential cred = new Credential(credentials.getSecretId(), credentials.getSecretKey());
    HttpProfile httpProfile = new HttpProfile();
    httpProfile.setEndpoint("cls." + currentRegion + ".tencentcloudapi.com");

    ClientProfile clientProfile = new ClientProfile();
    clientProfile.setHttpProfile(httpProfile);

    ClsClient clsClient = new ClsClient(cred, currentRegion, clientProfile);

    // If topic ID is already provided, verify it exists
    if (credentials.getTopic() != null && !credentials.getTopic().isEmpty()) {
      if (topicExists(clsClient, credentials.getTopic())) {
        return credentials.getTopic();
      }
    }

    // Need to create topic - first ensure logset exists
    String logsetId = ensureLogsetExists(clsClient, credentials);

    // Check if topic already exists by name in this logset
    String topicName = topic != null ? topic : "traceroot-topic";
    String existingTopicId = findExistingTopicByName(clsClient, logsetId, topicName);

    if (existingTopicId != null) {
      return existingTopicId;
    }

    // Create topic if it doesn't exist
    String topicId = createTopic(clsClient, logsetId, topicName);
    return topicId;
  }

  private boolean topicExists(ClsClient client, String topicId) {
    try {
      DescribeTopicsRequest request = new DescribeTopicsRequest();
      Filter filter = new Filter();
      filter.setKey("topicId");
      filter.setValues(new String[] {topicId});
      request.setFilters(new Filter[] {filter});

      DescribeTopicsResponse response = client.DescribeTopics(request);
      return response.getTopics() != null && response.getTopics().length > 0;
    } catch (Exception e) {
      System.err.println("[TraceRoot] DEBUG: Error checking topic existence: " + e.getMessage());
      return false;
    }
  }

  private String findExistingTopicByName(ClsClient client, String logsetId, String topicName) {
    try {
      DescribeTopicsRequest request = new DescribeTopicsRequest();
      Filter logsetFilter = new Filter();
      logsetFilter.setKey("logsetId");
      logsetFilter.setValues(new String[] {logsetId});

      Filter nameFilter = new Filter();
      nameFilter.setKey("topicName");
      nameFilter.setValues(new String[] {topicName});

      request.setFilters(new Filter[] {logsetFilter, nameFilter});

      DescribeTopicsResponse response = client.DescribeTopics(request);
      if (response.getTopics() != null && response.getTopics().length > 0) {
        return response.getTopics()[0].getTopicId();
      }
      return null;
    } catch (Exception e) {
      System.err.println(
          "[TraceRoot] DEBUG: Error finding existing topic by name: " + e.getMessage());
      return null;
    }
  }

  private String ensureLogsetExists(ClsClient client, TencentCredentials credentials)
      throws Exception {
    String logsetName = credentials.getLogset() != null ? credentials.getLogset() : "traceroot";

    // Check if logset already exists
    DescribeLogsetsRequest request = new DescribeLogsetsRequest();
    Filter filter = new Filter();
    filter.setKey("logsetName");
    filter.setValues(new String[] {logsetName});
    request.setFilters(new Filter[] {filter});

    DescribeLogsetsResponse response = client.DescribeLogsets(request);
    if (response.getLogsets() != null && response.getLogsets().length > 0) {
      String logsetId = response.getLogsets()[0].getLogsetId();
      return logsetId;
    }

    // Create new logset
    CreateLogsetRequest createRequest = new CreateLogsetRequest();
    createRequest.setLogsetName(logsetName);
    Tag tag = new Tag();
    tag.setKey("Source");
    tag.setValue("TraceRootSDK");
    createRequest.setTags(new Tag[] {tag});

    CreateLogsetResponse createResponse = client.CreateLogset(createRequest);
    String logsetId = createResponse.getLogsetId();
    return logsetId;
  }

  private String createTopic(ClsClient client, String logsetId, String topicName) throws Exception {
    CreateTopicRequest request = new CreateTopicRequest();
    request.setLogsetId(logsetId);
    request.setTopicName(topicName);
    Tag tag = new Tag();
    tag.setKey("Source");
    tag.setValue("TraceRootSDK");
    request.setTags(new Tag[] {tag});

    CreateTopicResponse response = client.CreateTopic(request);
    return response.getTopicId();
  }

  private void startBatchProcessor() {
    scheduler = LogAppenderUtils.createBatchProcessor("tencent-cls-appender", this::flushLogs, 2);
  }

  private void flushLogs() {
    if (logItemQueue.isEmpty() || clsClient == null) {
      return;
    }

    // Process all items in the queue
    List<LogItem> logItems = new ArrayList<>();
    LogItem logItem;

    while ((logItem = logItemQueue.poll()) != null && logItems.size() < 100) {
      logItems.add(logItem);
    }

    if (logItems.isEmpty()) {
      return;
    }

    // Send logs using CLS SDK - topic should be the topic ID
    String topicId =
        config.getTencentCredentials().getTopic() != null
            ? config.getTencentCredentials().getTopic()
            : topic;

    try {
      clsClient.putLogs(
          topicId,
          logItems,
          result -> {
            if (result.isSuccessful()) {
              processedLogs.addAndGet(logItems.size());
            } else {
              System.err.println(
                  "[TraceRoot] Failed to send logs to Tencent CLS: " + result.getErrorMessage());
              System.err.println("[TraceRoot] Error code: " + result.getErrorCode());
              addError("Failed to send logs to Tencent CLS: " + result.getErrorMessage());
            }
          });
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.err.println(
          "[TraceRoot] Interrupted while sending logs to Tencent CLS: " + e.getMessage());
      addError("Interrupted while sending logs to Tencent CLS: " + e.getMessage());
    } catch (Exception e) {
      System.err.println("[TraceRoot] Error sending logs to Tencent CLS: " + e.getMessage());
      addError("Error sending logs to Tencent CLS: " + e.getMessage());
    }
  }

  private LogItem createLogItem(ILoggingEvent event) {
    // Create timestamp in seconds (CLS expects seconds, not milliseconds)
    int timestamp = (int) (event.getTimeStamp() / 1000);
    LogItem logItem = new LogItem(timestamp);

    // Get common log metadata from utils
    Map<String, Object> baseMetadata = LogAppenderUtils.createBaseLogMetadata(event, config);

    // Add trace correlation (Tencent format - no AWS X-Ray formatting)
    LogAppenderUtils.addTraceCorrelation(baseMetadata, false);

    // Convert all metadata to LogContent entries
    for (Map.Entry<String, Object> entry : baseMetadata.entrySet()) {
      addLogContent(logItem, entry.getKey(), entry.getValue().toString());
    }

    return logItem;
  }

  private void addLogContent(LogItem logItem, String key, String value) {
    if (value != null) {
      logItem.PushBack(new LogContent(key, value));
    }
  }

  // Setters for configuration
  public void setConfig(TraceRootConfigImpl config) {
    this.config = config;
  }

  public void setLogset(String logset) {
    this.logset = logset;
  }

  public void setTopic(String topic) {
    this.topic = topic;
  }

  public void setRegion(String region) {
    this.region = region;
  }
}
