package ai.traceroot.sdk.logger.log4j2;

import ai.traceroot.sdk.config.TraceRootConfigImpl;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

/** Log4j2 console appender that outputs logs in TraceRoot format */
@Plugin(
    name = "Log4j2JsonConsoleAppender",
    category = Core.CATEGORY_NAME,
    elementType = Appender.ELEMENT_TYPE,
    printObject = true)
public class Log4j2JsonConsoleAppender extends AbstractAppender {

  private TraceRootConfigImpl config;

  protected Log4j2JsonConsoleAppender(
      String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions) {
    super(name, filter, layout, ignoreExceptions, Property.EMPTY_ARRAY);
  }

  @PluginFactory
  public static Log4j2JsonConsoleAppender createAppender(
      @PluginAttribute("name") String name,
      @PluginElement("Filter") Filter filter,
      @PluginElement("Layout") Layout<? extends Serializable> layout,
      @PluginAttribute(value = "ignoreExceptions", defaultBoolean = true)
          boolean ignoreExceptions) {
    if (name == null) {
      LOGGER.error("No name provided for Log4j2JsonConsoleAppender");
      return null;
    }
    return new Log4j2JsonConsoleAppender(name, filter, layout, ignoreExceptions);
  }

  @Override
  public void append(LogEvent event) {
    try {
      // For console, use standard log4j2 formatting with UTC timestamps
      // Format: timestamp [thread] LEVEL logger - message
      String timestamp =
          Instant.ofEpochMilli(event.getTimeMillis())
              .atZone(java.time.ZoneOffset.UTC)
              .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));

      String formattedMessage =
          String.format(
              "%s [%s] %-5s %s - %s",
              timestamp,
              event.getThreadName(),
              event.getLevel(),
              event.getLoggerName(),
              event.getMessage().getFormattedMessage());

      // Write directly to stdout
      byte[] bytes = (formattedMessage + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
      System.out.write(bytes);
      System.out.flush();
    } catch (Exception e) {
      LOGGER.error("Failed to append log event", e);
    }
  }

  public void setConfig(TraceRootConfigImpl config) {
    this.config = config;
  }
}
