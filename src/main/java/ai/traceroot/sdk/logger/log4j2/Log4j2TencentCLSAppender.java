package ai.traceroot.sdk.logger.log4j2;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.types.TencentCredentials;
import ai.traceroot.sdk.utils.LogAppenderUtils;
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
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

/**
 * Log4j2 appender for sending logs to Tencent Cloud CLS with trace correlation using official SDK
 */
@Plugin(
    name = "Log4j2TencentCLSAppender",
    category = Core.CATEGORY_NAME,
    elementType = Appender.ELEMENT_TYPE,
    printObject = true)
public class Log4j2TencentCLSAppender extends AbstractAppender {

  private String logset;
  private String topic;
  private String region = "ap-hongkong";
  private int maxQueueSize = 10000;
  private static final int ERROR_THROTTLE_INTERVAL = 1000;

  private AsyncProducerClient clsClient;
  private TraceRootConfigImpl config;
  private final Queue<LogItem> logItemQueue = new ConcurrentLinkedQueue<>();
  private ScheduledExecutorService scheduler;
  private final AtomicLong processedLogs = new AtomicLong(0);
  private final AtomicLong droppedLogs = new AtomicLong(0);

  protected Log4j2TencentCLSAppender(String name, Filter filter, boolean ignoreExceptions) {
    super(name, filter, null, ignoreExceptions, Property.EMPTY_ARRAY);
  }

  @PluginFactory
  public static Log4j2TencentCLSAppender createAppender(
      @PluginAttribute("name") String name,
      @PluginElement("Filter") Filter filter,
      @PluginAttribute(value = "ignoreExceptions", defaultBoolean = true)
          boolean ignoreExceptions) {
    if (name == null) {
      LOGGER.error("No name provided for Log4j2TencentCLSAppender");
      return null;
    }
    return new Log4j2TencentCLSAppender(name, filter, ignoreExceptions);
  }

  @Override
  public void start() {
    if (config == null) {
      // Config not set yet - appender will be configured programmatically later
      return;
    }

    if (!config.isEnableLogCloudExport()) {
      LOGGER.info("Tencent CLS logging disabled - appender will not start");
      return;
    }

    try {
      initializeCLSClient();
      startBatchProcessor();

      super.start();
      LOGGER.info(
          "Tencent CLS appender started successfully with topic=" + topic + ", region=" + region);
    } catch (Exception e) {
      LOGGER.error("Failed to start Tencent CLS appender: " + e.getMessage(), e);
      System.err.println("[TraceRoot] Failed to start Tencent CLS appender: " + e.getMessage());
      e.printStackTrace();
    }
  }

  @Override
  public void stop() {
    LogAppenderUtils.shutdownScheduler(scheduler, 10);

    // Flush remaining logs
    flushLogs();

    // Wait a bit for async client to send batched logs
    try {
      Thread.sleep(3000); // Wait 3 seconds for CLS SDK to send batched logs
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Close CLS client
    if (clsClient != null) {
      try {
        clsClient.close();
      } catch (Exception e) {
        System.err.println("[TraceRoot] Error closing CLS client: " + e.getMessage());
      }
    }

    super.stop();
    LOGGER.info("Tencent CLS appender stopped");
  }

  @Override
  public void append(LogEvent event) {
    if (!isStarted()) {
      return;
    }
    if (config == null || !config.isEnableLogCloudExport()) {
      return;
    }

    // Process the event immediately to capture stack trace while ThreadContext is available
    try {
      LogItem logItem = createLogItem(event);

      if (logItemQueue.size() >= maxQueueSize) {
        droppedLogs.incrementAndGet();
        if (droppedLogs.get() % ERROR_THROTTLE_INTERVAL == 1) {
          System.err.println(
              "[TraceRoot] Dropped "
                  + droppedLogs.get()
                  + " logs due to queue overflow. "
                  + "Consider increasing batch processing rate or reducing log volume.");
        }
        return;
      }

      logItemQueue.offer(logItem);
    } catch (Exception e) {
      System.err.println("[TraceRoot] Error creating log item: " + e.getMessage());
      e.printStackTrace();
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
            endpoint, credentials.getSecretId(), credentials.getSecretKey(), "TraceRootSDK");

    // Configure the producer
    producerConfig.setBatchSizeThresholdInBytes(512 * 1024); // 512KB
    producerConfig.setBatchCountThreshold(100);
    producerConfig.setLingerMs(2000); // 2 second linger time

    // Create the CLS client
    this.clsClient = new AsyncProducerClient(producerConfig);

    // Update the topic with actual topic ID
    if (credentials.getTopic() == null) {
      credentials.setTopic(actualTopicId);
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
      com.tencentcloudapi.cls.v20201016.models.Filter filter =
          new com.tencentcloudapi.cls.v20201016.models.Filter();
      filter.setKey("topicId");
      filter.setValues(new String[] {topicId});
      request.setFilters(new com.tencentcloudapi.cls.v20201016.models.Filter[] {filter});

      DescribeTopicsResponse response = client.DescribeTopics(request);
      return response.getTopics() != null && response.getTopics().length > 0;
    } catch (Exception e) {
      return false;
    }
  }

  private String findExistingTopicByName(ClsClient client, String logsetId, String topicName) {
    try {
      DescribeTopicsRequest request = new DescribeTopicsRequest();
      com.tencentcloudapi.cls.v20201016.models.Filter logsetFilter =
          new com.tencentcloudapi.cls.v20201016.models.Filter();
      logsetFilter.setKey("logsetId");
      logsetFilter.setValues(new String[] {logsetId});

      com.tencentcloudapi.cls.v20201016.models.Filter nameFilter =
          new com.tencentcloudapi.cls.v20201016.models.Filter();
      nameFilter.setKey("topicName");
      nameFilter.setValues(new String[] {topicName});

      request.setFilters(
          new com.tencentcloudapi.cls.v20201016.models.Filter[] {logsetFilter, nameFilter});

      DescribeTopicsResponse response = client.DescribeTopics(request);
      if (response.getTopics() != null && response.getTopics().length > 0) {
        return response.getTopics()[0].getTopicId();
      }
      return null;
    } catch (Exception e) {
      return null;
    }
  }

  private String ensureLogsetExists(ClsClient client, TencentCredentials credentials)
      throws Exception {
    // Construct logset name: serviceName-environment (e.g., tencent-log4j2-app-development)
    String logsetName;
    if (credentials.getLogset() != null) {
      logsetName = credentials.getLogset();
    } else {
      String serviceName =
          config.getInternalName() != null ? config.getInternalName() : "traceroot";
      String environment = config.getEnvironment() != null ? config.getEnvironment() : "production";
      logsetName = serviceName + "-" + environment;
    }

    // Check if logset already exists
    DescribeLogsetsRequest request = new DescribeLogsetsRequest();
    com.tencentcloudapi.cls.v20201016.models.Filter filter =
        new com.tencentcloudapi.cls.v20201016.models.Filter();
    filter.setKey("logsetName");
    filter.setValues(new String[] {logsetName});
    request.setFilters(new com.tencentcloudapi.cls.v20201016.models.Filter[] {filter});

    DescribeLogsetsResponse response = client.DescribeLogsets(request);
    if (response.getLogsets() != null && response.getLogsets().length > 0) {
      return response.getLogsets()[0].getLogsetId();
    }

    // Create new logset
    CreateLogsetRequest createRequest = new CreateLogsetRequest();
    createRequest.setLogsetName(logsetName);
    Tag tag = new Tag();
    tag.setKey("Source");
    tag.setValue("TraceRootSDK");
    createRequest.setTags(new Tag[] {tag});

    CreateLogsetResponse createResponse = client.CreateLogset(createRequest);
    return createResponse.getLogsetId();
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
    scheduler =
        LogAppenderUtils.createBatchProcessor("log4j2-tencent-cls-appender", this::flushLogs, 2);
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

    // Send logs using CLS SDK
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
              LOGGER.error("Failed to send logs to Tencent CLS: " + result.getErrorMessage());
            }
          });
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.err.println(
          "[TraceRoot] Interrupted while sending logs to Tencent CLS: " + e.getMessage());
    } catch (Exception e) {
      System.err.println("[TraceRoot] Error sending logs to Tencent CLS: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private LogItem createLogItem(LogEvent event) {
    // Create timestamp in seconds (CLS expects seconds, not milliseconds)
    int timestamp = (int) (event.getTimeMillis() / 1000);
    LogItem logItem = new LogItem(timestamp);

    // Get common log metadata from utils
    Map<String, Object> baseMetadata = Log4j2LogAppenderUtils.createBaseLogMetadata(event, config);

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
