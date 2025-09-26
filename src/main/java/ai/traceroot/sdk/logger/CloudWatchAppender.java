package ai.traceroot.sdk.logger;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.types.AwsCredentials;
import ai.traceroot.sdk.utils.LogAppenderUtils;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
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

    if (config == null) {
      String error = "TraceRoot config not set";
      addError(error);
      System.err.println("[TraceRoot] CloudWatch appender error: " + error);
      return;
    }

    if (!config.isEnableLogCloudExport()) {
      String info = "CloudWatch logging disabled - appender will not start";
      addInfo(info);
      return;
    }

    try {
      initializeCloudWatchClient();
      ensureLogGroupAndStream();
      startBatchProcessor();

      super.start();
      String success = "CloudWatch appender started successfully";
      addInfo(success);
    } catch (Exception e) {
      String error = "Failed to start CloudWatch appender: " + e.getMessage();
      addError(error, e);
      System.err.println("[TraceRoot] " + error);
      e.printStackTrace();
    }
  }

  @Override
  public void stop() {
    LogAppenderUtils.shutdownScheduler(scheduler, 10);

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
          System.err.println("[TraceRoot] Could not create log group: " + e.getMessage());
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
        System.err.println("[TraceRoot] Could not create log group: " + ex.getMessage());
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
          System.err.println("[TraceRoot] Could not create log stream: " + e.getMessage());
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
        System.err.println("[TraceRoot] Could not create log stream: " + ex.getMessage());
      }
    }
  }

  private void startBatchProcessor() {
    scheduler =
        LogAppenderUtils.createBatchProcessor(
            "cloudwatch-log-appender", this::flushLogs, flushIntervalSeconds);
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
    // Get common log metadata from utils
    Map<String, Object> logData = LogAppenderUtils.createBaseLogMetadata(event, config);

    // Add trace correlation with AWS X-Ray formatting
    LogAppenderUtils.addTraceCorrelation(logData, true);

    return logData;
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
