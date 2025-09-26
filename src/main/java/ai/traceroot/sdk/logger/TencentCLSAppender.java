package ai.traceroot.sdk.logger;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.types.TencentCredentials;
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
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom Logback appender for sending logs to Tencent Cloud CLS with trace correlation using
 * official SDK
 */
public class TencentCLSAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

  private String logset; // Equivalent to AWS Log Group (not used in CLS SDK)
  private String topic; // Equivalent to AWS Log Stream - this will be the topic ID
  private String region = "ap-hongkong";

  // Internal state
  private AsyncProducerClient clsClient;
  private TraceRootConfigImpl config;
  private final Queue<ILoggingEvent> logEventQueue = new ConcurrentLinkedQueue<>();
  private ScheduledExecutorService scheduler;
  private final AtomicLong processedLogs = new AtomicLong(0);

  @Override
  public void start() {

    if (config == null) {
      String error = "TraceRoot config not set";
      addError(error);
      System.err.println("[TraceRoot] Tencent CLS appender error: " + error);
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

    if (scheduler != null) {
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
          scheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        scheduler.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }

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

    // Add event to queue for batch processing
    logEventQueue.offer(event);
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
    scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "tencent-cls-appender");
              t.setDaemon(true);
              return t;
            });

    // Schedule regular flushing every 2 seconds
    scheduler.scheduleAtFixedRate(this::flushLogs, 2, 2, TimeUnit.SECONDS);
  }

  private void flushLogs() {
    if (logEventQueue.isEmpty() || clsClient == null) {
      return;
    }

    // Process all events in the queue
    List<LogItem> logItems = new ArrayList<>();
    ILoggingEvent event;

    while ((event = logEventQueue.poll()) != null && logItems.size() < 100) {
      try {
        LogItem logItem = createLogItem(event);
        logItems.add(logItem);
      } catch (Exception e) {
        System.err.println("[TraceRoot] Error creating log item: " + e.getMessage());
      }
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

    // Add all the structured log data as LogContent
    addLogContent(
        logItem,
        "service_name",
        config.getServiceName() != null ? config.getServiceName() : "tencent-standalone-app");

    addLogContent(
        logItem,
        "github_commit_hash",
        config.getGithubCommitHash() != null ? config.getGithubCommitHash() : "main");

    addLogContent(
        logItem,
        "github_owner",
        config.getGithubOwner() != null ? config.getGithubOwner() : "traceroot-ai");

    addLogContent(
        logItem,
        "github_repo_name",
        config.getGithubRepoName() != null ? config.getGithubRepoName() : "traceroot-sdk-java");

    addLogContent(
        logItem,
        "environment",
        config.getEnvironment() != null ? config.getEnvironment() : "development");

    // Add MDC context if available
    Map<String, String> mdc = event.getMDCPropertyMap();
    if (mdc != null && !mdc.isEmpty()) {
      String requestId = mdc.get("requestId");
      if (requestId != null) {
        addLogContent(logItem, "requestId", requestId);
      }
    }

    // Add stack trace
    String stackTrace = getCallerStackTrace(event);
    addLogContent(logItem, "stack_trace", stackTrace);

    // Add log level
    addLogContent(logItem, "level", event.getLevel().toString().toLowerCase());

    // Add message
    addLogContent(logItem, "message", event.getFormattedMessage());

    // Add formatted timestamp
    Instant instant = Instant.ofEpochMilli(event.getTimeStamp());
    java.time.ZonedDateTime zdt = instant.atZone(java.time.ZoneId.systemDefault());
    String formattedTimestamp =
        zdt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"));
    addLogContent(logItem, "timestamp", formattedTimestamp);

    // Add trace correlation
    addTraceCorrelation(logItem);

    return logItem;
  }

  private void addLogContent(LogItem logItem, String key, String value) {
    if (value != null) {
      logItem.PushBack(new LogContent(key, value));
    }
  }

  private void addTraceCorrelation(LogItem logItem) {
    Span span = Span.current();
    if (span != null) {
      SpanContext spanContext = span.getSpanContext();
      if (spanContext.isValid()
          && !spanContext.getTraceId().equals("00000000000000000000000000000000")) {
        addLogContent(logItem, "trace_id", spanContext.getTraceId());
        addLogContent(logItem, "span_id", spanContext.getSpanId());
      } else {
        addLogContent(logItem, "trace_id", "no-trace");
        addLogContent(logItem, "span_id", "no-span");
      }
    } else {
      addLogContent(logItem, "trace_id", "no-trace");
      addLogContent(logItem, "span_id", "no-span");
    }
  }

  private String getCallerStackTrace(ILoggingEvent event) {
    StackTraceElement[] callerData = event.getCallerData();
    if (callerData != null && callerData.length > 0) {
      StackTraceElement caller = findActualCaller(callerData);
      if (caller != null) {
        String methodName = caller.getMethodName();
        int lineNumber = caller.getLineNumber();

        // Clean up AspectJ synthetic method names
        if (methodName.contains("_aroundBody")) {
          methodName = methodName.replaceAll("_aroundBody\\d*", "");
        }

        String filePath = getFilePath(caller);

        // Apply root path transformation if configured
        if (config != null
            && config.getRootPath() != null
            && filePath.startsWith(config.getRootPath())) {
          filePath = filePath.substring(config.getRootPath().length());
          if (filePath.startsWith("/")) {
            filePath = filePath.substring(1);
          }
        }

        return String.format("%s:%s:%d", filePath, methodName, lineNumber);
      }
    }
    return "Unknown:unknown:0";
  }

  private StackTraceElement findActualCaller(StackTraceElement[] callerData) {
    for (StackTraceElement element : callerData) {
      String className = element.getClassName();
      if (!className.contains("TraceRootLogger")
          && !className.contains("LoggerFactory")
          && !className.contains("Logger")
          && !className.startsWith("org.slf4j")
          && !className.startsWith("ch.qos.logback")
          && !className.startsWith("java.util.concurrent")
          && !className.startsWith("java.lang.Thread")) {
        return element;
      }
    }
    return callerData.length > 0 ? callerData[0] : null;
  }

  private String getFilePath(StackTraceElement caller) {
    String className = caller.getClassName();
    String fileName = caller.getFileName();

    if (fileName == null) {
      String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
      fileName = simpleClassName + ".java";
    }

    // Convert package to path
    String packagePath = className.replace('.', '/');
    int lastSlash = packagePath.lastIndexOf('/');
    if (lastSlash >= 0) {
      packagePath = packagePath.substring(0, lastSlash + 1) + fileName;
    } else {
      packagePath = fileName;
    }

    return packagePath;
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
