package com.traceroot.sdk.logger;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.traceroot.sdk.config.TraceRootConfigImpl;
import com.traceroot.sdk.types.AwsCredentials;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

/** Custom Logback appender for sending logs to AWS CloudWatch Logs with trace correlation */
public class CloudWatchAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

  private String logGroupName;
  private String logStreamName;
  private String region = "us-west-2";
  private int batchSize = 100;
  private int flushIntervalSeconds = 5;
  private int maxRetries = 3;

  // Internal state
  private CloudWatchLogsClient cloudWatchClient;
  private TraceRootConfigImpl config;
  private final Queue<InputLogEvent> logEventQueue = new ConcurrentLinkedQueue<>();
  private ScheduledExecutorService scheduler;
  private final AtomicLong sequenceToken = new AtomicLong(0);
  private final ObjectMapper objectMapper =
      new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

  @Override
  public void start() {
    System.out.println("[TraceRoot] CloudWatchAppender.start() called");

    if (config == null) {
      String error = "TraceRoot config not set";
      addError(error);
      System.err.println("[TraceRoot] CloudWatch appender error: " + error);
      return;
    }

    if (!config.isEnableLogCloudExport()) {
      String info = "CloudWatch logging disabled - appender will not start";
      addInfo(info);
      System.out.println("[TraceRoot] " + info);
      return;
    }

    try {
      System.out.println("[TraceRoot] Initializing CloudWatch client...");
      initializeCloudWatchClient();

      System.out.println("[TraceRoot] Ensuring log group and stream...");
      ensureLogGroupAndStream();

      System.out.println("[TraceRoot] Starting batch processor...");
      startBatchProcessor();

      super.start();
      String success = "CloudWatch appender started successfully";
      addInfo(success);
      System.out.println("[TraceRoot] " + success);
    } catch (Exception e) {
      String error = "Failed to start CloudWatch appender: " + e.getMessage();
      addError(error, e);
      System.err.println("[TraceRoot] " + error);
      e.printStackTrace();
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

    if (cloudWatchClient != null) {
      cloudWatchClient.close();
    }

    super.stop();
    addInfo("CloudWatch appender stopped");
  }

  @Override
  protected void append(ILoggingEvent event) {
    if (!isStarted() || config == null || !config.isEnableLogCloudExport()) {
      return;
    }

    try {
      // Create log event with trace correlation
      Map<String, Object> logData = createLogData(event);
      String message = objectMapper.writeValueAsString(logData);

      InputLogEvent logEvent =
          InputLogEvent.builder().timestamp(event.getTimeStamp()).message(message).build();

      logEventQueue.offer(logEvent);

      // Flush if queue is getting full
      if (logEventQueue.size() >= batchSize) {
        flushLogs();
      }
    } catch (Exception e) {
      addError("Failed to append log event", e);
    }
  }

  private void initializeCloudWatchClient() throws Exception {
    AwsCredentials credentials = config.getAwsCredentials();
    if (credentials == null) {
      throw new IllegalStateException("AWS credentials not available");
    }

    System.out.println(
        "[TraceRoot] AWS credentials - Access Key: "
            + (credentials.getAccessKeyId() != null
                ? credentials.getAccessKeyId().substring(0, 4) + "..."
                : "null")
            + ", Secret Key: "
            + (credentials.getSecretAccessKey() != null ? "***" : "null")
            + ", Session Token: "
            + (credentials.getSessionToken() != null ? "***" : "null"));

    if (credentials.getAccessKeyId() == null || credentials.getSecretAccessKey() == null) {
      throw new IllegalStateException("AWS credentials have null access key or secret key");
    }

    StaticCredentialsProvider credentialsProvider;
    if (credentials.getSessionToken() != null) {
      credentialsProvider =
          StaticCredentialsProvider.create(
              AwsSessionCredentials.create(
                  credentials.getAccessKeyId(),
                  credentials.getSecretAccessKey(),
                  credentials.getSessionToken()));
    } else {
      credentialsProvider =
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(
                  credentials.getAccessKeyId(), credentials.getSecretAccessKey()));
    }

    this.cloudWatchClient =
        CloudWatchLogsClient.builder()
            .region(Region.of(credentials.getRegion() != null ? credentials.getRegion() : region))
            .credentialsProvider(credentialsProvider)
            .build();
  }

  private void ensureLogGroupAndStream() throws Exception {
    // Check if log group exists first
    try {
      DescribeLogGroupsRequest describeRequest =
          DescribeLogGroupsRequest.builder().logGroupNamePrefix(logGroupName).build();

      DescribeLogGroupsResponse response = cloudWatchClient.describeLogGroups(describeRequest);

      boolean logGroupExists =
          response.logGroups().stream()
              .anyMatch(group -> group.logGroupName().equals(logGroupName));

      if (!logGroupExists) {
        try {
          cloudWatchClient.createLogGroup(
              CreateLogGroupRequest.builder().logGroupName(logGroupName).build());
          addInfo("Created log group: " + logGroupName);
        } catch (Exception e) {
          System.out.println("[TraceRoot] Could not create log group: " + e.getMessage());
        }
      }
    } catch (Exception e) {
      // If we can't check, try to create anyway
      try {
        cloudWatchClient.createLogGroup(
            CreateLogGroupRequest.builder().logGroupName(logGroupName).build());
        addInfo("Created log group: " + logGroupName);
      } catch (ResourceAlreadyExistsException ex) {
        // Already exists, ignore
      } catch (Exception ex) {
        System.out.println("[TraceRoot] Could not create log group: " + ex.getMessage());
      }
    }

    // Check if log stream exists first
    try {
      DescribeLogStreamsRequest describeRequest =
          DescribeLogStreamsRequest.builder()
              .logGroupName(logGroupName)
              .logStreamNamePrefix(logStreamName)
              .build();

      DescribeLogStreamsResponse response = cloudWatchClient.describeLogStreams(describeRequest);

      boolean logStreamExists =
          response.logStreams().stream()
              .anyMatch(stream -> stream.logStreamName().equals(logStreamName));

      if (!logStreamExists) {
        try {
          cloudWatchClient.createLogStream(
              CreateLogStreamRequest.builder()
                  .logGroupName(logGroupName)
                  .logStreamName(logStreamName)
                  .build());
          addInfo("Created log stream: " + logStreamName);
        } catch (Exception e) {
          System.out.println("[TraceRoot] Could not create log stream: " + e.getMessage());
        }
      }
    } catch (Exception e) {
      // If we can't check, try to create anyway
      try {
        cloudWatchClient.createLogStream(
            CreateLogStreamRequest.builder()
                .logGroupName(logGroupName)
                .logStreamName(logStreamName)
                .build());
        addInfo("Created log stream: " + logStreamName);
      } catch (ResourceAlreadyExistsException ex) {
        // Already exists, ignore
      } catch (Exception ex) {
        System.out.println("[TraceRoot] Could not create log stream: " + ex.getMessage());
      }
    }
  }

  private void startBatchProcessor() {
    scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "cloudwatch-log-appender");
              t.setDaemon(true);
              return t;
            });

    scheduler.scheduleAtFixedRate(
        this::flushLogs, flushIntervalSeconds, flushIntervalSeconds, TimeUnit.SECONDS);
  }

  private void flushLogs() {
    if (logEventQueue.isEmpty() || cloudWatchClient == null) {
      return;
    }

    List<InputLogEvent> batch = new ArrayList<>();
    for (int i = 0; i < batchSize && !logEventQueue.isEmpty(); i++) {
      InputLogEvent event = logEventQueue.poll();
      if (event != null) {
        batch.add(event);
      }
    }

    if (batch.isEmpty()) {
      return;
    }

    // Sort by timestamp (required by CloudWatch)
    batch.sort(Comparator.comparing(InputLogEvent::timestamp));

    for (int attempt = 0; attempt < maxRetries; attempt++) {
      try {
        PutLogEventsRequest.Builder requestBuilder =
            PutLogEventsRequest.builder()
                .logGroupName(logGroupName)
                .logStreamName(logStreamName)
                .logEvents(batch);

        // Add sequence token if we have one
        long currentToken = sequenceToken.get();
        if (currentToken > 0) {
          requestBuilder.sequenceToken(String.valueOf(currentToken));
        }

        PutLogEventsResponse response = cloudWatchClient.putLogEvents(requestBuilder.build());

        // Update sequence token for next batch
        if (response.nextSequenceToken() != null) {
          try {
            sequenceToken.set(Long.parseLong(response.nextSequenceToken()));
          } catch (NumberFormatException e) {
            // Keep current sequence token
          }
        }

        if (config.isTracerVerbose()) {
          addInfo("Successfully sent " + batch.size() + " log events to CloudWatch");
        }
        break; // Success, exit retry loop

      } catch (Exception e) {
        if (attempt == maxRetries - 1) {
          addError("Failed to send logs to CloudWatch after " + maxRetries + " attempts", e);
        } else {
          addWarn(
              "Failed to send logs to CloudWatch, retrying... (attempt "
                  + (attempt + 1)
                  + "/"
                  + maxRetries
                  + ")");
          try {
            Thread.sleep(1000 * (attempt + 1)); // Exponential backoff
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      }
    }
  }

  private Map<String, Object> createLogData(ILoggingEvent event) {
    // Use LinkedHashMap to preserve field order
    Map<String, Object> logData = new LinkedHashMap<>();

    // Follow exact field order from expected format
    // 1. service_name
    if (config != null) {
      logData.put(
          "service_name",
          config.getServiceName() != null ? config.getServiceName() : "standalone-java-app");
    } else {
      logData.put("service_name", "standalone-java-app");
    }

    // 2. github_commit_hash
    if (config != null) {
      logData.put(
          "github_commit_hash",
          config.getGithubCommitHash() != null ? config.getGithubCommitHash() : "main");
    } else {
      logData.put("github_commit_hash", "main");
    }

    // 3. github_owner
    if (config != null) {
      logData.put(
          "github_owner",
          config.getGithubOwner() != null ? config.getGithubOwner() : "traceroot-ai");
    } else {
      logData.put("github_owner", "traceroot-ai");
    }

    // 4. github_repo_name
    if (config != null) {
      logData.put(
          "github_repo_name",
          config.getGithubRepoName() != null ? config.getGithubRepoName() : "traceroot-sdk-java");
    } else {
      logData.put("github_repo_name", "traceroot-sdk-java");
    }

    // 5. environment
    if (config != null) {
      logData.put(
          "environment", config.getEnvironment() != null ? config.getEnvironment() : "development");
    } else {
      logData.put("environment", "development");
    }

    // 6. userId (single field, not userId_0 and userId_1)
    Map<String, String> mdc = event.getMDCPropertyMap();
    if (mdc != null && !mdc.isEmpty()) {
      String userId = mdc.get("userId");
      logData.put("userId", userId != null ? userId : "user123");
    } else {
      logData.put("userId", "user123");
    }

    // 7. requestId (from MDC if available)
    if (mdc != null && !mdc.isEmpty()) {
      String requestId = mdc.get("requestId");
      if (requestId != null) {
        logData.put("requestId", requestId);
      }
    }

    // 8. stack_trace
    String stackTrace = getCallerStackTrace(event);
    logData.put("stack_trace", stackTrace);

    // 9. level (lowercase)
    logData.put("level", event.getLevel().toString().toLowerCase());

    // 10. message
    logData.put("message", event.getFormattedMessage());

    // 11. timestamp (in format: "2025-09-17 15:05:14,717")
    Instant instant = Instant.ofEpochMilli(event.getTimeStamp());
    java.time.ZonedDateTime zdt = instant.atZone(java.time.ZoneId.systemDefault());
    String timestamp =
        zdt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"));
    logData.put("timestamp", timestamp);

    // 12. trace_id and span_id
    Span span = Span.current();
    if (span != null) {
      SpanContext spanContext = span.getSpanContext();
      if (spanContext.isValid()
          && !spanContext.getTraceId().equals("00000000000000000000000000000000")) {
        // Format trace_id in AWS X-Ray format: "1-{first 8 chars}-{remaining 24 chars}"
        String traceIdHex = spanContext.getTraceId();
        String formattedTraceId =
            String.format("1-%s-%s", traceIdHex.substring(0, 8), traceIdHex.substring(8));
        logData.put("trace_id", formattedTraceId);
        logData.put("span_id", spanContext.getSpanId());
      } else {
        logData.put("trace_id", "no-trace");
        logData.put("span_id", "no-span");
      }
    } else {
      logData.put("trace_id", "no-trace");
      logData.put("span_id", "no-span");
    }

    return logData;
  }

  private String getCallerStackTrace(ILoggingEvent event) {
    StackTraceElement[] callerData = event.getCallerData();
    if (callerData != null && callerData.length > 0) {
      StringBuilder stackTrace = new StringBuilder();

      // Build a call chain similar to TypeScript format
      for (int i = 0; i < Math.min(callerData.length, 3); i++) {
        StackTraceElement caller = callerData[i];
        String className = caller.getClassName();
        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
        String methodName = caller.getMethodName();
        int lineNumber = caller.getLineNumber();

        if (stackTrace.length() > 0) {
          stackTrace.append(" -> ");
        }

        // Format: "ClassName.java:methodName:lineNumber"
        stackTrace.append(String.format("%s.java:%s:%d", simpleClassName, methodName, lineNumber));
      }

      return stackTrace.toString();
    }
    return "Unknown.java:unknown:0";
  }

  // Setters for configuration
  public void setConfig(TraceRootConfigImpl config) {
    this.config = config;
  }

  public void setLogGroupName(String logGroupName) {
    this.logGroupName = logGroupName;
  }

  public void setLogStreamName(String logStreamName) {
    this.logStreamName = logStreamName;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public void setFlushIntervalSeconds(int flushIntervalSeconds) {
    this.flushIntervalSeconds = flushIntervalSeconds;
  }

  public void setMaxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
  }
}
