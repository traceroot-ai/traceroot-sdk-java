package ai.traceroot.sdk.logger.log4j2;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import ai.traceroot.sdk.types.AwsCredentials;
import ai.traceroot.sdk.utils.LogAppenderUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
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
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

/** Log4j2 appender for sending logs to AWS CloudWatch Logs with trace correlation */
@Plugin(
    name = "Log4j2CloudWatchAppender",
    category = Core.CATEGORY_NAME,
    elementType = Appender.ELEMENT_TYPE,
    printObject = true)
public class Log4j2CloudWatchAppender extends AbstractAppender {

  private String logGroupName;
  private String logStreamName;
  private String region = "us-west-2";
  private int batchSize = 100;
  private int flushIntervalSeconds = 5;
  private int maxRetries = 3;

  private CloudWatchLogsClient cloudWatchClient;
  private TraceRootConfigImpl config;
  private final Queue<InputLogEvent> logEventQueue = new ConcurrentLinkedQueue<>();
  private ScheduledExecutorService scheduler;
  private final AtomicLong sequenceToken = new AtomicLong(0);
  private final ObjectMapper objectMapper =
      new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

  protected Log4j2CloudWatchAppender(String name, Filter filter, boolean ignoreExceptions) {
    super(name, filter, null, ignoreExceptions, Property.EMPTY_ARRAY);
  }

  @PluginFactory
  public static Log4j2CloudWatchAppender createAppender(
      @PluginAttribute("name") String name,
      @PluginElement("Filter") Filter filter,
      @PluginAttribute(value = "ignoreExceptions", defaultBoolean = true)
          boolean ignoreExceptions) {
    if (name == null) {
      LOGGER.error("No name provided for Log4j2CloudWatchAppender");
      return null;
    }
    return new Log4j2CloudWatchAppender(name, filter, ignoreExceptions);
  }

  @Override
  public void start() {
    if (config == null) {
      // Config not set yet - appender will be configured programmatically later
      return;
    }

    if (!config.isEnableLogCloudExport()) {
      LOGGER.info("CloudWatch logging disabled - appender will not start");
      return;
    }

    try {
      initializeCloudWatchClient();
      ensureLogGroupAndStream();
      startBatchProcessor();

      super.start();
      LOGGER.info("CloudWatch appender started successfully");
    } catch (Exception e) {
      LOGGER.error("Failed to start CloudWatch appender: " + e.getMessage(), e);
      System.err.println("[TraceRoot] Failed to start CloudWatch appender: " + e.getMessage());
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
    LOGGER.info("CloudWatch appender stopped");
  }

  @Override
  public void append(LogEvent event) {
    if (!isStarted() || config == null || !config.isEnableLogCloudExport()) {
      return;
    }

    try {
      // Create log event with trace correlation
      Map<String, Object> logData = createLogData(event);
      String message = objectMapper.writeValueAsString(logData);

      InputLogEvent logEvent =
          InputLogEvent.builder().timestamp(event.getTimeMillis()).message(message).build();

      logEventQueue.offer(logEvent);

      // Flush if queue is getting full
      if (logEventQueue.size() >= batchSize) {
        flushLogs();
      }
    } catch (Exception e) {
      LOGGER.error("Failed to append log event", e);
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
          LOGGER.info("Created log group: " + logGroupName);
        } catch (Exception e) {
          System.err.println("[TraceRoot] Could not create log group: " + e.getMessage());
        }
      }
    } catch (Exception e) {
      try {
        cloudWatchClient.createLogGroup(
            CreateLogGroupRequest.builder().logGroupName(logGroupName).build());
        LOGGER.info("Created log group: " + logGroupName);
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
          LOGGER.info("Created log stream: " + logStreamName);
        } catch (Exception e) {
          System.err.println("[TraceRoot] Could not create log stream: " + e.getMessage());
        }
      }
    } catch (Exception e) {
      try {
        cloudWatchClient.createLogStream(
            CreateLogStreamRequest.builder()
                .logGroupName(logGroupName)
                .logStreamName(logStreamName)
                .build());
        LOGGER.info("Created log stream: " + logStreamName);
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
            "log4j2-cloudwatch-appender", this::flushLogs, flushIntervalSeconds);
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
          LOGGER.info("Successfully sent " + batch.size() + " log events to CloudWatch");
        }
        break; // Success, exit retry loop

      } catch (Exception e) {
        if (attempt == maxRetries - 1) {
          LOGGER.error("Failed to send logs to CloudWatch after " + maxRetries + " attempts", e);
        } else {
          LOGGER.warn(
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

  private Map<String, Object> createLogData(LogEvent event) {
    // Get common log metadata from utils
    Map<String, Object> logData = Log4j2LogAppenderUtils.createBaseLogMetadata(event, config);

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
